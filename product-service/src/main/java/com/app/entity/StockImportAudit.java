package com.app.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "stock_import_audits")
public class StockImportAudit {
    @Id
    private String id;
    private String fileName;
    private long fileSize;
    private String sha256;
    private String uploadedById;
    private String uploadedBy;
    private Instant uploadedAt;
    private String status;
    private String failureReason;
    private int updatedRows;
    private int updatedProducts;
    private int totalRows;
    private int processedRows;
    private int successRows;
    private int failureRows;
    private List<StockChange> changes = new ArrayList<>();
    private List<RowFailure> failures = new ArrayList<>();

    protected StockImportAudit() {}

    public StockImportAudit(String fileName, long fileSize, String sha256, String uploadedById, String uploadedBy) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.sha256 = sha256;
        this.uploadedById = uploadedById;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = Instant.now();
        this.status = "PROCESSING";
    }

    public void startProcessing(int totalRows, List<RowFailure> failures) {
        this.status = "APPLYING";
        this.totalRows = totalRows;
        this.processedRows = failures.size();
        this.failureRows = failures.size();
        this.failures = new ArrayList<>(failures);
        this.failureReason = null;
    }

    public void recordSuccess(StockChange change) {
        this.processedRows++;
        this.successRows++;
        this.updatedRows++;
        this.changes.add(change);
    }

    public void recordFailure(RowFailure failure) {
        this.processedRows++;
        this.failureRows++;
        this.failures.add(failure);
    }

    public void removeProductSuccesses(String productCode, String message) {
        List<StockChange> failedChanges = changes.stream()
                .filter(change -> change.productCode().equals(productCode))
                .toList();
        changes.removeIf(change -> change.productCode().equals(productCode));
        for (StockChange change : failedChanges) {
            successRows--;
            updatedRows--;
            failureRows++;
            failures.add(new RowFailure(change.rowNumber(), change.productCode(), change.color(), message));
        }
    }

    public void complete() {
        this.status = failureRows == 0 ? "COMPLETED" : successRows == 0 ? "FAILED" : "COMPLETED_WITH_ERRORS";
        this.updatedProducts = (int) changes.stream().map(StockChange::productCode).distinct().count();
        this.processedRows = totalRows;
    }

    public void fail(String failureReason) {
        this.status = "FAILED";
        this.failureReason = failureReason;
        if (totalRows == 0) {
            totalRows = 1;
            processedRows = 1;
            failureRows = 1;
            failures.add(new RowFailure(0, "", "", failureReason));
        } else if (processedRows < totalRows) {
            failureRows += totalRows - processedRows;
            processedRows = totalRows;
            failures.add(new RowFailure(0, "", "", failureReason));
        }
    }

    public void setSha256(String sha256) { this.sha256 = sha256; }
    public String getId() { return id; }
    public String getFileName() { return fileName; }
    public long getFileSize() { return fileSize; }
    public String getSha256() { return sha256; }
    public String getUploadedById() { return uploadedById; }
    public String getUploadedBy() { return uploadedBy; }
    public Instant getUploadedAt() { return uploadedAt; }
    public String getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public int getUpdatedRows() { return updatedRows; }
    public int getUpdatedProducts() { return updatedProducts; }
    public int getTotalRows() { return totalRows; }
    public int getProcessedRows() { return processedRows; }
    public int getSuccessRows() { return successRows; }
    public int getFailureRows() { return failureRows; }
    public int getProgressPercent() {
        if ("COMPLETED".equals(status) || "COMPLETED_WITH_ERRORS".equals(status) || "FAILED".equals(status)) {
            return 100;
        }
        return totalRows == 0 ? 0 : Math.min(95, processedRows * 90 / totalRows);
    }
    public List<StockChange> getChanges() { return changes; }
    public List<RowFailure> getFailures() { return failures; }

    public record StockChange(
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
