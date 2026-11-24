package com.app.response;

import java.time.Instant;
import java.util.List;

public record StockImportAuditResponse(
        String id,
        String fileName,
        long fileSize,
        String sha256,
        String uploadedById,
        String uploadedBy,
        Instant uploadedAt,
        String status,
        String failureReason,
        int totalRows,
        int processedRows,
        int successRows,
        int failureRows,
        int progressPercent,
        int updatedRows,
        int updatedProducts,
        List<StockChange> changes,
        List<RowFailure> failures) {
    public record StockChange(
            String productCode,
            String productName,
            String category,
            String productType,
            String color,
            int previousStockCount,
            int stockCount,
            int rowNumber) {}
    public record RowFailure(int rowNumber, String productCode, String color, String message) {}
}
