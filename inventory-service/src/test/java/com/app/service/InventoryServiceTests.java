package com.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.app.entity.InventoryItem;
import com.app.entity.ProcessedOrder;
import com.app.event.InventoryEvent;
import com.app.event.OrderEvent;
import com.app.event.StockSyncEvent;
import com.app.repository.InventoryRepository;
import com.app.repository.ProcessedOrderRepository;
import com.mongodb.client.result.UpdateResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.kafka.core.KafkaTemplate;

class InventoryServiceTests {
    @Test
    void quantityComparisonSupportsAllOperatorsAndRejectsUnknownOnes() {
        InventoryRepository inventory = org.mockito.Mockito.mock(InventoryRepository.class);
        ProcessedOrderRepository processed = org.mockito.Mockito.mock(ProcessedOrderRepository.class);
        MongoTemplate mongo = org.mockito.Mockito.mock(MongoTemplate.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<Object, Object> events = org.mockito.Mockito.mock(KafkaTemplate.class);
        InventoryStockPublisher stockPublisher = org.mockito.Mockito.mock(InventoryStockPublisher.class);
        when(mongo.count(any(Query.class), eq(InventoryItem.class))).thenReturn(0L);
        when(mongo.find(any(Query.class), eq(InventoryItem.class))).thenReturn(List.of());
        InventoryService service = new InventoryService(inventory, processed, mongo, events, stockPublisher);

        for (String operator : List.of("eq", "gte", "lte", "gt", "lt", "ne")) {
            service.search(null, null, null, null, null, operator, 10,
                    null, null, 0, 20, "updatedAt", "desc");
        }
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> service.search(null, null, null, null, null, "invalid", 10,
                        null, null, 0, 20, "updatedAt", "desc"));
    }

    @Test
    void productStockUpdateCreatesInventoryRecordWithCatalogQuantity() {
        InventoryRepository inventory = org.mockito.Mockito.mock(InventoryRepository.class);
        ProcessedOrderRepository processed = org.mockito.Mockito.mock(ProcessedOrderRepository.class);
        MongoTemplate mongo = org.mockito.Mockito.mock(MongoTemplate.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<Object, Object> events = org.mockito.Mockito.mock(KafkaTemplate.class);
        InventoryStockPublisher stockPublisher = org.mockito.Mockito.mock(InventoryStockPublisher.class);
        when(inventory.findById("product-1:variant-1")).thenReturn(Optional.empty());
        when(inventory.save(any(InventoryItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        InventoryService service = new InventoryService(inventory, processed, mongo, events, stockPublisher);

        service.syncFromProduct(new StockSyncEvent("STOCK_SET", "product-1", "variant-1", "Product", "Black", 12));

        org.mockito.ArgumentCaptor<InventoryItem> itemCaptor = org.mockito.ArgumentCaptor.forClass(InventoryItem.class);
        verify(inventory).save(itemCaptor.capture());
        assertEquals(12, itemCaptor.getValue().getQuantity());
        assertEquals("product-1:variant-1", itemCaptor.getValue().getId());
        verifyNoInteractions(stockPublisher);
    }

    @Test
    void catalogSyncStoresHumanReadableProductCode() {
        InventoryRepository inventory = org.mockito.Mockito.mock(InventoryRepository.class);
        ProcessedOrderRepository processed = org.mockito.Mockito.mock(ProcessedOrderRepository.class);
        MongoTemplate mongo = org.mockito.Mockito.mock(MongoTemplate.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<Object, Object> events = org.mockito.Mockito.mock(KafkaTemplate.class);
        InventoryStockPublisher stockPublisher = org.mockito.Mockito.mock(InventoryStockPublisher.class);
        when(inventory.findById("product-1:variant-1")).thenReturn(Optional.empty());
        when(inventory.save(any(InventoryItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        InventoryService service = new InventoryService(inventory, processed, mongo, events, stockPublisher);

        service.syncFromProduct(new StockSyncEvent(
                "STOCK_SET", "product-1", "SKU-123", "variant-1", "Product", "Black", 12));

        ArgumentCaptor<InventoryItem> itemCaptor = ArgumentCaptor.forClass(InventoryItem.class);
        verify(inventory).save(itemCaptor.capture());
        assertEquals("SKU-123", itemCaptor.getValue().getProductCode());
    }

    @Test
    void explicitSyncToProductPublishesCurrentInventoryItem() {
        InventoryRepository inventory = org.mockito.Mockito.mock(InventoryRepository.class);
        ProcessedOrderRepository processed = org.mockito.Mockito.mock(ProcessedOrderRepository.class);
        MongoTemplate mongo = org.mockito.Mockito.mock(MongoTemplate.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<Object, Object> events = org.mockito.Mockito.mock(KafkaTemplate.class);
        InventoryStockPublisher stockPublisher = org.mockito.Mockito.mock(InventoryStockPublisher.class);
        InventoryItem item = new InventoryItem("product-1", "variant-1", "Product", "Black", 12);
        when(inventory.findById("product-1:variant-1")).thenReturn(Optional.of(item));
        InventoryService service = new InventoryService(inventory, processed, mongo, events, stockPublisher);

        service.syncToProduct("product-1", "variant-1");

        verify(stockPublisher).publishAndWait(item);
    }

    @Test
    void catalogSeedCreatesMissingInventoryButDoesNotOverwriteExistingAvailability() {
        InventoryRepository inventory = org.mockito.Mockito.mock(InventoryRepository.class);
        ProcessedOrderRepository processed = org.mockito.Mockito.mock(ProcessedOrderRepository.class);
        MongoTemplate mongo = org.mockito.Mockito.mock(MongoTemplate.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<Object, Object> events = org.mockito.Mockito.mock(KafkaTemplate.class);
        InventoryStockPublisher stockPublisher = org.mockito.Mockito.mock(InventoryStockPublisher.class);
        InventoryItem existing = new InventoryItem("product-1", "variant-1", "Product", "Black", 3);
        when(inventory.findById("product-1:variant-1")).thenReturn(Optional.of(existing));
        when(inventory.save(any(InventoryItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        InventoryService service = new InventoryService(inventory, processed, mongo, events, stockPublisher);

        service.syncFromProduct(new StockSyncEvent("STOCK_SEED", "product-1", "variant-1", "Renamed", "Black", 10));

        assertEquals(3, existing.getQuantity());
        assertEquals("Renamed", existing.getProductName());
        verify(inventory).save(existing);
        verifyNoInteractions(stockPublisher);
    }

    @Test
    void catalogReconciliationSetsExistingInventoryToProductStockCount() {
        InventoryRepository inventory = org.mockito.Mockito.mock(InventoryRepository.class);
        ProcessedOrderRepository processed = org.mockito.Mockito.mock(ProcessedOrderRepository.class);
        MongoTemplate mongo = org.mockito.Mockito.mock(MongoTemplate.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<Object, Object> events = org.mockito.Mockito.mock(KafkaTemplate.class);
        InventoryStockPublisher stockPublisher = org.mockito.Mockito.mock(InventoryStockPublisher.class);
        InventoryItem existing = new InventoryItem("product-1", "variant-1", "Product", "Black", 3);
        when(inventory.findById("product-1:variant-1")).thenReturn(Optional.of(existing));
        when(inventory.save(any(InventoryItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        InventoryService service = new InventoryService(inventory, processed, mongo, events, stockPublisher);

        service.syncFromProduct(new StockSyncEvent(
                "STOCK_RECONCILE", "product-1", "variant-1", "Product", "Black", 12));

        assertEquals(12, existing.getQuantity());
        verify(inventory).save(existing);
        verifyNoInteractions(stockPublisher);
    }

    @Test
    void reservesAvailableItemsAndKeepsThemWhenAnotherItemIsUnavailable() {
        InventoryRepository inventory = org.mockito.Mockito.mock(InventoryRepository.class);
        ProcessedOrderRepository processed = org.mockito.Mockito.mock(ProcessedOrderRepository.class);
        MongoTemplate mongo = org.mockito.Mockito.mock(MongoTemplate.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<Object, Object> events = org.mockito.Mockito.mock(KafkaTemplate.class);
        InventoryStockPublisher stockPublisher = org.mockito.Mockito.mock(InventoryStockPublisher.class);
        when(processed.existsById("order-1")).thenReturn(false);
        when(processed.save(any(ProcessedOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mongo.updateFirst(any(), any(), eq(com.app.entity.InventoryItem.class)))
                .thenReturn(UpdateResult.acknowledged(1L, 1L, null), UpdateResult.acknowledged(0L, 0L, null));
        when(inventory.findById("product-b:variant-b")).thenReturn(Optional.empty());
        when(events.send(anyString(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        InventoryService service = new InventoryService(inventory, processed, mongo, events, stockPublisher);
        OrderEvent order = new OrderEvent("event-1", "ORDER_CREATED", "order-1", "user-1", "user@example.com",
                List.of(
                        new OrderEvent.OrderItem("product-a", "variant-a", "Available item",
                                "Black", 1, BigDecimal.TEN),
                        new OrderEvent.OrderItem("product-b", "variant-b", "Unavailable item",
                                "Blue", 1, BigDecimal.ONE)),
                null, new BigDecimal("11"), Instant.now());

        service.handle(order);

        ArgumentCaptor<ProcessedOrder> reservationCaptor = ArgumentCaptor.forClass(ProcessedOrder.class);
        verify(processed).save(reservationCaptor.capture());
        assertEquals("PARTIALLY_RESERVED", reservationCaptor.getValue().getStatus());
        assertEquals(List.of(new ProcessedOrder.Reservation("product-a:variant-a", 1)),
                reservationCaptor.getValue().getReservations());
        verify(mongo, times(2)).updateFirst(any(), any(), eq(com.app.entity.InventoryItem.class));

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(events).send(eq("commerce.inventory-events"), eq("order-1"), eventCaptor.capture());
        InventoryEvent inventoryEvent = (InventoryEvent) eventCaptor.getValue();
        assertEquals("PARTIALLY_RESERVED", inventoryEvent.status());
        assertEquals(List.of(
                new InventoryEvent.ItemResult(0, "RESERVED", "Inventory reserved"),
                new InventoryEvent.ItemResult(1, "REJECTED",
                        "Insufficient stock for Unavailable item (Blue); inventory item not found")),
                inventoryEvent.itemResults());
    }
}
