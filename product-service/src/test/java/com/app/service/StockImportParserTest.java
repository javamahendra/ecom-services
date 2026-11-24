package com.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class StockImportParserTest {
    private final StockImportParser parser = new StockImportParser();

    @Test
    void parsesCsvRowsAndNormalizesProductCode() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "stock.csv", "text/csv",
                "productCode,color,stockCount\nabc123def4,Black,12\n".getBytes());

        var parsed = parser.parse(file);

        assertEquals(1, parsed.totalRows());
        assertEquals(1, parsed.rows().size());
        assertEquals("ABC123DEF4", parsed.rows().getFirst().productCode());
        assertEquals("Black", parsed.rows().getFirst().color());
        assertEquals(12, parsed.rows().getFirst().stockCount());
    }

    @Test
    void parsesExcelStockRows() throws Exception {
        byte[] workbookBytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Stock");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("productCode");
            header.createCell(1).setCellValue("color");
            header.createCell(2).setCellValue("stockCount");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("ABC123DEF4");
            row.createCell(1).setCellValue("Silver");
            row.createCell(2).setCellValue(8);
            workbook.write(output);
            workbookBytes = output.toByteArray();
        }
        MockMultipartFile file = new MockMultipartFile(
                "file", "stock.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                workbookBytes);

        var parsed = parser.parse(file);

        assertEquals(1, parsed.totalRows());
        assertEquals(1, parsed.rows().size());
        assertEquals("Silver", parsed.rows().getFirst().color());
        assertEquals(8, parsed.rows().getFirst().stockCount());
    }

    @Test
    void reportsNegativeStockCountAsARowFailure() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "stock.csv", "text/csv",
                "productCode,color,stockCount\nABC123DEF4,Black,-1\n".getBytes());

        var parsed = parser.parse(file);
        assertEquals(1, parsed.totalRows());
        assertEquals(1, parsed.failures().size());
        assertEquals(2, parsed.failures().getFirst().rowNumber());
    }
}
