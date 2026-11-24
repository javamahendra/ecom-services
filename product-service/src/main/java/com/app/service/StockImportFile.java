package com.app.service;

import java.util.List;

record StockImportFile(
        List<StockImportRow> rows,
        List<StockImportFailure> failures,
        int totalRows) {}
