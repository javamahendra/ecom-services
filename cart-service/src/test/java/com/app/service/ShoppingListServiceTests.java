package com.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.app.entity.ShoppingList;
import com.app.repository.ShoppingListRepository;
import com.app.request.AddCartItemRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ShoppingListServiceTests {
    @Mock
    private ShoppingListRepository lists;
    @Mock
    private ProductCatalogClient catalog;
    private ShoppingListService service;

    @BeforeEach
    void setUp() {
        service = new ShoppingListService(lists, catalog);
        when(lists.findByUserId("user-1")).thenReturn(Optional.empty());
    }

    @Test
    void addToCartStoresCatalogSnapshotAndCalculatesSubtotal() {
        when(lists.save(any(ShoppingList.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(catalog.getItem("product-1", "variant-1")).thenReturn(
                new ProductCatalogClient.ProductSnapshot("product-1", "Laptop", "Electronics",
                        "Laptop", "variant-1", "Black", new BigDecimal("100.00"),
                        List.of("https://images.example/laptop.jpg"), true, 5));

        var response = service.addToCart("user-1", new AddCartItemRequest("product-1", "variant-1", 2));

        assertEquals(2, response.itemCount());
        assertEquals(new BigDecimal("200.00"), response.subtotal());
        assertEquals("Laptop", response.items().getFirst().getProductName());
        assertEquals(List.of("https://images.example/laptop.jpg"), response.items().getFirst().getImageUrls());
        verify(lists).save(any(ShoppingList.class));
    }

    @Test
    void addToCartRejectsQuantityAboveAvailableStock() {
        when(catalog.getItem("product-1", "variant-1")).thenReturn(
                new ProductCatalogClient.ProductSnapshot("product-1", "Laptop", "Electronics",
                        "Laptop", "variant-1", "Black", new BigDecimal("100.00"),
                        List.of(), true, 1));

        assertThrows(ResponseStatusException.class,
                () -> service.addToCart("user-1", new AddCartItemRequest("product-1", "variant-1", 2)));
    }

    @Test
    void addingSameItemAgainIncreasesExistingQuantity() {
        AtomicReference<ShoppingList> stored = new AtomicReference<>();
        when(lists.findByUserId("user-1")).thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(lists.save(any(ShoppingList.class))).thenAnswer(invocation -> {
            ShoppingList list = invocation.getArgument(0);
            stored.set(list);
            return list;
        });
        when(catalog.getItem("product-1", "variant-1")).thenReturn(
                new ProductCatalogClient.ProductSnapshot("product-1", "Laptop", "Electronics",
                        "Laptop", "variant-1", "Black", new BigDecimal("100.00"),
                        List.of(), true, 10));

        service.addToCart("user-1", new AddCartItemRequest("product-1", "variant-1", 2));
        var response = service.addToCart("user-1", new AddCartItemRequest("product-1", "variant-1", 3));

        assertEquals(1, response.items().size());
        assertEquals(5, response.items().getFirst().getQuantity());
        assertEquals(5, response.itemCount());
        assertEquals(new BigDecimal("500.00"), response.subtotal());
    }
}
