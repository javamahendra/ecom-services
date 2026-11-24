package com.app.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Component
class StockImportParser {
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final int MAX_ROWS = 5_000;
    private static final List<String> REQUIRED_HEADERS = List.of("productcode", "color", "stockcount");

    StockImportFile parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badRequest("Choose a non-empty CSV or Excel file.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw badRequest("Stock import files must be 10 MB or smaller.");
        }
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw badRequest("The uploaded file must have a .csv, .xlsx, or .xls extension.");
        }
        String lowerName = filename.toLowerCase(Locale.ROOT);
        try {
            if (lowerName.endsWith(".csv")) {
                return parseCsv(file);
            }
            if (lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls")) {
                return parseExcel(file);
            }
            throw badRequest("Only .csv, .xlsx, and .xls files are supported.");
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Could not read the uploaded stock file. Check its format and contents.", exception);
        }
    }

    private StockImportFile parseCsv(MultipartFile file) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                     new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder().setIgnoreEmptyLines(true).build().parse(reader)) {
            var records = parser.getRecords();
            if (records.isEmpty()) {
                throw badRequest("The stock import file has no header row.");
            }
            if (records.size() - 1 > MAX_ROWS) {
                throw badRequest("Stock imports are limited to 5,000 rows.");
            }
            Map<String, Integer> columns = headerColumns(
                    java.util.stream.IntStream.range(0, records.getFirst().size())
                            .mapToObj(index -> records.getFirst().get(index)).toList());
            List<StockImportRow> rows = new ArrayList<>();
            List<StockImportFailure> failures = new ArrayList<>();
            int totalRows = 0;
            for (int index = 1; index < records.size(); index++) {
                var record = records.get(index);
                List<String> values = new ArrayList<>();
                for (int column = 0; column < record.size(); column++) {
                    values.add(record.get(column));
                }
                if (values.stream().anyMatch(value -> !value.isBlank())) {
                    totalRows++;
                    addRow(rows, failures, index + 1, values, columns);
                }
            }
            return requireRows(rows, failures, totalRows);
        }
    }

    private StockImportFile parseExcel(MultipartFile file) throws Exception {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) {
                throw badRequest("The Excel file has no worksheets.");
            }
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getLastRowNum() > MAX_ROWS) {
                throw badRequest("Stock imports are limited to 5,000 rows.");
            }
            Row header = sheet.getRow(0);
            if (header == null) {
                throw badRequest("The stock import file has no header row.");
            }
            DataFormatter formatter = new DataFormatter();
            List<String> headers = new ArrayList<>();
            for (int column = 0; column < header.getLastCellNum(); column++) {
                headers.add(formatter.formatCellValue(header.getCell(column)));
            }
            Map<String, Integer> columns = headerColumns(headers);
            List<StockImportRow> rows = new ArrayList<>();
            List<StockImportFailure> failures = new ArrayList<>();
            int totalRows = 0;
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                List<String> values = new ArrayList<>();
                for (int column = 0; column < row.getLastCellNum(); column++) {
                    values.add(formatter.formatCellValue(row.getCell(column)));
                }
                if (values.stream().anyMatch(value -> !value.isBlank())) {
                    totalRows++;
                    addRow(rows, failures, rowIndex + 1, values, columns);
                }
            }
            return requireRows(rows, failures, totalRows);
        }
    }

    private Map<String, Integer> headerColumns(List<String> headers) {
        Map<String, Integer> columns = new HashMap<>();
        for (int index = 0; index < headers.size(); index++) {
            String header = headers.get(index).replace("\uFEFF", "").trim().toLowerCase(Locale.ROOT);
            if (header.equals("productid")) {
                header = "productcode";
            }
            if (!header.isEmpty() && columns.putIfAbsent(header, index) != null) {
                throw badRequest("The stock import file contains a duplicate column: " + header);
            }
        }
        for (String required : REQUIRED_HEADERS) {
            if (!columns.containsKey(required)) {
                throw badRequest("Missing required column: " + required
                        + ". Required columns are productCode, color, and stockCount.");
            }
        }
        return columns;
    }

    private void addRow(List<StockImportRow> rows, List<StockImportFailure> failures,
                        int rowNumber, List<String> values,
                        Map<String, Integer> columns) {
        String productCode = value(values, columns.get("productcode")).trim();
        String color = value(values, columns.get("color")).trim();
        String stockValue = value(values, columns.get("stockcount")).trim();
        if (productCode.isEmpty() || color.isEmpty() || stockValue.isEmpty()) {
            failures.add(new StockImportFailure(rowNumber, productCode, color,
                    "Row must include productCode, color, and stockCount."));
            return;
        }
        int stockCount;
        try {
            stockCount = new java.math.BigDecimal(stockValue).intValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            failures.add(new StockImportFailure(rowNumber, productCode, color,
                    "stockCount must be a whole number."));
            return;
        }
        if (stockCount < 0) {
            failures.add(new StockImportFailure(rowNumber, productCode, color,
                    "stockCount cannot be negative."));
            return;
        }
        rows.add(new StockImportRow(rowNumber, productCode.toUpperCase(Locale.ROOT), color, stockCount));
    }

    private String value(List<String> values, int index) {
        return index < values.size() ? values.get(index) : "";
    }

    private StockImportFile requireRows(
            List<StockImportRow> rows, List<StockImportFailure> failures, int totalRows) {
        if (totalRows == 0) {
            throw badRequest("The stock import file contains no stock rows.");
        }
        if (totalRows > MAX_ROWS) throw badRequest("Stock imports are limited to 5,000 non-empty rows.");
        return new StockImportFile(List.copyOf(rows), List.copyOf(failures), totalRows);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
