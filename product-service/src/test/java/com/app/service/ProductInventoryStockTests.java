package com.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.app.entity.Product;
import com.app.entity.ProductVariant;
import com.app.event.InventoryStockChangedEvent;
import com.app.repository.ProductRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

class ProductInventoryStockTests {
    @Test
    void metadataSyncPublishesCatalogDetailsWithoutReconciliation() {
        ProductRepository products = org.mockito.Mockito.mock(ProductRepository.class);
        MongoTemplate mongo = org.mockito.Mockito.mock(MongoTemplate.class);
        ProductInventorySync inventorySync = org.mockito.Mockito.mock(ProductInventorySync.class);
        Product product = new Product("Desk", "", "Home", "Furniture", "Black",
                BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN, LocalDate.now(),
                List.of(new ProductVariant("Black", List.of(), 8)));
        when(products.findAll()).thenReturn(List.of(product));
        ProductService service = new ProductService(products, mongo, inventorySync);

        assertEquals(Map.of("syncedVariants", 1), service.syncInventoryMetadata());

        verify(inventorySync).publishMetadataSnapshot(org.mockito.ArgumentMatchers.eq(product),
                org.mockito.ArgumentMatchers.anyList());
        org.mockito.Mockito.verify(inventorySync, org.mockito.Mockito.never())
                .reconcileSnapshot(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void inventoryStockChangesAreReflectedInProductVariantAndAggregate() {
        ProductRepository products = org.mockito.Mockito.mock(ProductRepository.class);
        MongoTemplate mongo = org.mockito.Mockito.mock(MongoTemplate.class);
        ProductInventorySync inventorySync = org.mockito.Mockito.mock(ProductInventorySync.class);
        ProductVariant variant = new ProductVariant("Black", List.of(), 8);
        Product product = new Product("Desk", "", "Home", "Furniture", "Black",
                BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN, LocalDate.now(), List.of(variant));
        when(products.findById(product.getId())).thenReturn(Optional.of(product));
        when(products.save(product)).thenReturn(product);
        ProductService service = new ProductService(products, mongo, inventorySync);

        service.applyInventoryStock(new InventoryStockChangedEvent(
                product.getId(), variant.getId(), "Desk", "Black", 5));

        assertEquals(5, variant.getStockCount());
        assertEquals(5, product.getStockCount());
        verify(products).save(product);
    }
}
