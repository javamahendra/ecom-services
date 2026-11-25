package com.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.app.entity.Product;
import com.app.entity.ProductVariant;
import com.app.entity.StockImportAudit;
import com.app.repository.ProductRepository;
import com.app.repository.StockImportAuditRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

class ProductStockImportServiceTest {
    @Test
    void recordsUploaderFileAndVariantStockChange() {
        ProductRepository products = org.mockito.Mockito.mock(ProductRepository.class);
        StockImportAuditRepository audits = org.mockito.Mockito.mock(StockImportAuditRepository.class);
        ProductInventorySync inventorySync = org.mockito.Mockito.mock(ProductInventorySync.class);
        Product product = new Product("Laptop", "Portable computer", "Computers", "Laptop",
                "Black", BigDecimal.valueOf(999), BigDecimal.ZERO, BigDecimal.valueOf(999),
                LocalDate.now(), List.of(new ProductVariant("Black", List.of("https://example.test/image.jpg"), 4)));
        product.assignProductCode("ABC123DEF4");
        when(audits.save(any(StockImportAudit.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(products.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(products.findAllByProductCodeIn(anyList())).thenReturn(List.of(product));

        ProductStockImportService service = new ProductStockImportService(
                products, audits, new StockImportParser(), inventorySync);
        MockMultipartFile file = new MockMultipartFile("file", "weekly-stock.csv", "text/csv",
                "productCode,color,stockCount\nABC123DEF4,Black,12\n".getBytes());

        var response = service.importStock(file, new StockImportActor("user-123", "warehouse-manager"));

        assertEquals(1, response.updatedRows());
        assertEquals(4, response.updates().getFirst().previousStockCount());
        assertEquals(12, response.updates().getFirst().stockCount());
        verify(inventorySync).publish(any(Product.class), any(ProductVariant.class));
        ArgumentCaptor<StockImportAudit> auditCaptor = ArgumentCaptor.forClass(StockImportAudit.class);
        verify(audits, org.mockito.Mockito.atLeastOnce()).save(auditCaptor.capture());
        StockImportAudit audit = auditCaptor.getAllValues().getLast();
        assertEquals("COMPLETED", audit.getStatus());
        assertEquals("weekly-stock.csv", audit.getFileName());
        assertEquals("user-123", audit.getUploadedById());
        assertEquals("warehouse-manager", audit.getUploadedBy());
        assertEquals(1, audit.getTotalRows());
        assertEquals(1, audit.getProcessedRows());
        assertEquals(1, audit.getSuccessRows());
        assertEquals(0, audit.getFailureRows());
        assertEquals(100, audit.getProgressPercent());
        assertEquals("Laptop", audit.getChanges().getFirst().productType());
        assertEquals(4, audit.getChanges().getFirst().previousStockCount());
        assertEquals(12, audit.getChanges().getFirst().stockCount());
    }

    @Test
    void appliesValidRowsAndReportsInvalidRowsSeparately() {
        ProductRepository products = org.mockito.Mockito.mock(ProductRepository.class);
        StockImportAuditRepository audits = org.mockito.Mockito.mock(StockImportAuditRepository.class);
        ProductInventorySync inventorySync = org.mockito.Mockito.mock(ProductInventorySync.class);
        Product product = new Product("Laptop", "Portable computer", "Computers", "Laptop",
                "Black", BigDecimal.valueOf(999), BigDecimal.ZERO, BigDecimal.valueOf(999),
                LocalDate.now(), List.of(new ProductVariant("Black", List.of("https://example.test/image.jpg"), 4)));
        product.assignProductCode("ABC123DEF4");
        when(audits.save(any(StockImportAudit.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(products.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(products.findAllByProductCodeIn(anyList())).thenReturn(List.of(product));

        ProductStockImportService service = new ProductStockImportService(
                products, audits, new StockImportParser(), inventorySync);
        MockMultipartFile file = new MockMultipartFile("file", "weekly-stock.csv", "text/csv",
                ("productCode,color,stockCount\n"
                        + "ABC123DEF4,Black,12\n"
                        + "UNKNOWN1234,Black,8\n"
                        + "ABC123DEF4,Silver,-3\n").getBytes());

        var response = service.importStock(file, new StockImportActor("user-123", "warehouse-manager"));

        assertEquals(3, response.totalRows());
        assertEquals(1, response.successRows());
        assertEquals(2, response.failureRows());
        ArgumentCaptor<StockImportAudit> auditCaptor = ArgumentCaptor.forClass(StockImportAudit.class);
        verify(audits, org.mockito.Mockito.atLeastOnce()).save(auditCaptor.capture());
        assertEquals("COMPLETED_WITH_ERRORS", auditCaptor.getAllValues().getLast().getStatus());
    }
}
