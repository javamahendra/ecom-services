package com.app.response;

import java.util.List;

public record StockImportResponse(
        String importId, int totalRows, int successRows, int failureRows,
        int updatedRows, int updatedProducts, List<UpdatedStock> updates, List<RowFailure> failures) {
    public record UpdatedStock(
        int rowNumber,
        String productCode,
            String productName,
            String category,
            String productType,
            String color,
            int previousStockCount,
            int stockCount) {}
    public record RowFailure(int rowNumber, String productCode, String color, String message) {}
}
