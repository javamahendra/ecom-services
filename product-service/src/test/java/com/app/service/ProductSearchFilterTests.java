package com.app.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.app.entity.Product;
import com.app.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.web.server.ResponseStatusException;

class ProductSearchFilterTests {
    @Test
    void rejectsInvalidPriceStockAndDiscountRangesBeforeQuerying() {
        ProductService service = new ProductService(
                org.mockito.Mockito.mock(ProductRepository.class),
                org.mockito.Mockito.mock(MongoTemplate.class),
                org.mockito.Mockito.mock(ProductInventorySync.class));

        assertThrows(ResponseStatusException.class, () -> service.search(
                null, null, null, null, BigDecimal.TEN, BigDecimal.ONE, null,
                null, null, null, null, null, null, null, null, null, "createdAt", "desc", 0, 20));
        assertThrows(ResponseStatusException.class, () -> service.search(
                null, null, null, null, null, null, null,
                10, 2, null, null, null, null, null, null, null, "createdAt", "desc", 0, 20));
        assertThrows(ResponseStatusException.class, () -> service.search(
                null, null, null, null, null, null, null,
                null, null, null, null, null, BigDecimal.valueOf(80), BigDecimal.valueOf(20),
                null, null, "createdAt", "desc", 0, 20));
        assertThrows(ResponseStatusException.class, () -> service.search(
                null, null, null, null, null, null, null,
                null, null, "invalid", 10, null, null, null, null, null, "createdAt", "desc", 0, 20));
        assertThrows(ResponseStatusException.class, () -> service.search(
                null, null, null, null, null, null, null,
                null, null, "lte", null, null, null, null, null, null, "createdAt", "desc", 0, 20));
        assertThrows(ResponseStatusException.class, () -> service.search(
                null, null, null, null, null, null, null,
                null, null, null, null, -1, null, null, null, null, "createdAt", "desc", 0, 20));
    }

    @Test
    void filtersProductsByExactColorVariantCount() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.count(any(Query.class), eq(Product.class))).thenReturn(0L);
        when(mongo.find(any(Query.class), eq(Product.class))).thenReturn(List.of());
        ProductService service = new ProductService(
                mock(ProductRepository.class), mongo, mock(ProductInventorySync.class));

        service.search(null, null, null, null, null, null, null, null, null, null, null,
                2, null, null, null, null, "createdAt", "desc", 0, 20);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongo).count(queryCaptor.capture(), eq(Product.class));
        assertTrue(queryCaptor.getValue().getQueryObject().toJson().contains("\"$size\": 2"));
    }
}
