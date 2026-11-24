package com.app.service;

import com.app.entity.Product;
import com.app.entity.ProductVariant;
import com.app.entity.StockImportAudit;
import com.app.entity.StockImportAudit.StockChange;
import com.app.repository.ProductRepository;
import com.app.repository.StockImportAuditRepository;
import com.app.response.PageResponse;
import com.app.response.StockImportAuditResponse;
import com.app.response.StockImportResponse;
import com.app.response.StockImportResponse.UpdatedStock;
import com.app.response.StockImportResponse.RowFailure;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProductStockImportService {
    private static final Logger logger = LoggerFactory.getLogger(ProductStockImportService.class);

    private final ProductRepository products;
    private final StockImportAuditRepository audits;
    private final StockImportParser parser;

    public ProductStockImportService(
            ProductRepository products, StockImportAuditRepository audits, StockImportParser parser) {
        this.products = products;
        this.audits = audits;
        this.parser = parser;
    }

    public StockImportResponse importStock(MultipartFile file, StockImportActor actor) {
        StockImportAudit audit = audits.save(new StockImportAudit(
                safeFileName(file), file == null ? 0 : file.getSize(), null,
                actor.id(), actor.displayName()));
        try {
            audit.setSha256(calculateSha256(file));
            audits.save(audit);
            return applyStockImport(file, audit);
        } catch (RuntimeException exception) {
            audit.fail(safeFailureReason(exception));
            try {
                audits.save(audit);
            } catch (RuntimeException auditException) {
                exception.addSuppressed(auditException);
                logger.error("Could not save failed stock import audit: importId={}", audit.getId(), auditException);
            }
            throw exception;
        }
    }

    public PageResponse<StockImportAuditResponse> findImportHistory(int page, int size) {
        if (page < 0) throw badRequest("page must be zero or greater");
        if (size < 1 || size > 100) throw badRequest("size must be between 1 and 100");
        Page<StockImportAudit> result = audits.findAllByOrderByUploadedAtDesc(PageRequest.of(page, size));
        List<StockImportAuditResponse> content = result.getContent().stream().map(audit ->
                new StockImportAuditResponse(
                        audit.getId(), audit.getFileName(), audit.getFileSize(), audit.getSha256(),
                        audit.getUploadedById(), audit.getUploadedBy(), audit.getUploadedAt(), audit.getStatus(),
                        audit.getFailureReason(), audit.getTotalRows(), audit.getProcessedRows(),
                        audit.getSuccessRows(), audit.getFailureRows(), audit.getProgressPercent(),
                        audit.getUpdatedRows(), audit.getUpdatedProducts(),
                        audit.getChanges().stream().map(change -> new StockImportAuditResponse.StockChange(
                                change.productCode(), change.productName(), change.category(), change.productType(),
                                change.color(), change.previousStockCount(), change.stockCount(), change.rowNumber())).toList(),
                        audit.getFailures().stream().map(failure -> new StockImportAuditResponse.RowFailure(
                                failure.rowNumber(), failure.productCode(), failure.color(), failure.message())).toList()))
                .toList();
        return new PageResponse<>(content, page, size, result.getTotalElements(), result.getTotalPages(),
                result.isFirst(), result.isLast());
    }

    private StockImportResponse applyStockImport(MultipartFile file, StockImportAudit audit) {
        StockImportFile parsed = parser.parse(file);
        List<StockImportRow> rows = parsed.rows();
        List<String> codes = rows.stream().map(StockImportRow::productCode).distinct().toList();
        Map<String, Product> productsByCode = products.findAllByProductCodeIn(codes).stream()
                .collect(Collectors.toMap(Product::getProductCode, product -> product));
        List<com.app.entity.StockImportAudit.RowFailure> initialFailures = parsed.failures().stream()
                .map(failure -> new com.app.entity.StockImportAudit.RowFailure(
                        failure.rowNumber(), failure.productCode(), failure.color(), failure.message()))
                .toList();
        audit.startProcessing(parsed.totalRows(), initialFailures);
        audits.save(audit);
        Set<String> seenVariants = new HashSet<>();
        Map<String, List<UpdatedStock>> updatesByProduct = new HashMap<>();
        int handledSinceSave = 0;
        for (StockImportRow row : rows) {
            try {
                Product product = productsByCode.get(row.productCode());
                if (product == null) {
                    throw new IllegalArgumentException("Unknown product code.");
                }
                String variantKey = row.productCode() + ":" + row.color().toLowerCase(Locale.ROOT);
                if (!seenVariants.add(variantKey)) {
                    throw new IllegalArgumentException("Duplicate productCode and color row.");
                }
                long matchingVariants = product.getVariants().stream()
                        .filter(variant -> variant.getColor().equalsIgnoreCase(row.color()))
                        .count();
                if (matchingVariants == 0) throw new IllegalArgumentException("Unknown product color.");
                if (matchingVariants > 1) throw new IllegalArgumentException("Ambiguous product color.");
                ProductVariant variant = product.getVariants().stream()
                        .filter(item -> item.getColor().equalsIgnoreCase(row.color()))
                        .findFirst().orElseThrow();
                int previousStockCount = variant.getStockCount();
                product.updateVariantStockByColor(row.color(), row.stockCount());
                UpdatedStock update = new UpdatedStock(row.rowNumber(), product.getProductCode(), product.getName(),
                        product.getCategory(), product.getProductType(), variant.getColor(), previousStockCount,
                        row.stockCount());
                updatesByProduct.computeIfAbsent(product.getProductCode(), ignored -> new ArrayList<>()).add(update);
                audit.recordSuccess(new StockChange(row.rowNumber(), product.getProductCode(), product.getName(),
                        product.getCategory(), product.getProductType(), variant.getColor(), previousStockCount,
                        row.stockCount()));
            } catch (IllegalArgumentException | org.springframework.web.server.ResponseStatusException exception) {
                audit.recordFailure(new com.app.entity.StockImportAudit.RowFailure(
                        row.rowNumber(), row.productCode(), row.color(), exception.getMessage()));
            }
            if (++handledSinceSave >= 25) {
                audits.save(audit);
                handledSinceSave = 0;
            }
        }

        for (String productCode : updatesByProduct.keySet()) {
            try {
                products.save(productsByCode.get(productCode));
            } catch (RuntimeException exception) {
                audit.removeProductSuccesses(productCode, "Could not save product stock changes.");
                logger.error("Could not save imported stock for productCode={}", productCode, exception);
            }
            audits.save(audit);
        }
        audit.complete();
        audits.save(audit);
        List<UpdatedStock> updates = audit.getChanges().stream().map(change -> new UpdatedStock(
                change.rowNumber(), change.productCode(), change.productName(), change.category(),
                change.productType(), change.color(), change.previousStockCount(), change.stockCount())).toList();
        List<RowFailure> failures = audit.getFailures().stream().map(failure -> new RowFailure(
                failure.rowNumber(), failure.productCode(), failure.color(), failure.message())).toList();
        logger.info("Bulk stock import completed: rows={}, success={}, failures={}",
                audit.getTotalRows(), audit.getSuccessRows(), audit.getFailureRows());
        return new StockImportResponse(audit.getId(), audit.getTotalRows(), audit.getSuccessRows(),
                audit.getFailureRows(), audit.getUpdatedRows(), audit.getUpdatedProducts(),
                List.copyOf(updates), List.copyOf(failures));
    }

    private String calculateSha256(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > 10 * 1024 * 1024) return null;
        try (InputStream input = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Could not calculate the uploaded file checksum.", exception);
        }
    }

    private String safeFileName(MultipartFile file) {
        if (file == null || file.getOriginalFilename() == null) return "(unnamed file)";
        String normalized = file.getOriginalFilename().replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        return separator >= 0 ? normalized.substring(separator + 1) : normalized;
    }

    private String safeFailureReason(RuntimeException exception) {
        String reason = exception instanceof ResponseStatusException statusException
                ? statusException.getReason() : "Stock import failed while processing or saving changes.";
        if (reason == null || reason.isBlank()) return "Stock import failed.";
        return reason.length() > 500 ? reason.substring(0, 500) : reason;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
