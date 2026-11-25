package com.app.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.app.event.InventoryEvent;
import com.app.event.OrderEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DispatchRecordTests {
    @Test
    void partialReservationDispatchesOnlyAvailableItems() {
        List<OrderEvent.OrderItem> items = List.of(
                new OrderEvent.OrderItem("product-a", "variant-a", "Available item", "Black",
                        2, new BigDecimal("10.00")),
                new OrderEvent.OrderItem("product-b", "variant-b", "Unavailable item", "Blue",
                        1, new BigDecimal("5.00")));
        DispatchRecord record = new DispatchRecord(new OrderEvent(
                "event-1", "ORDER_CREATED", "order-1", "user-1", "user@example.com", null,
                items, null, new BigDecimal("25.00"), Instant.now()));

        record.applyInventoryResult("PARTIALLY_RESERVED", List.of(
                new InventoryEvent.ItemResult(0, "RESERVED", "Inventory reserved"),
                new InventoryEvent.ItemResult(1, "REJECTED", "Insufficient stock")), 
                "1 item reserved; 1 item unavailable");

        assertEquals("READY_TO_DISPATCH", record.getStatus());
        assertEquals(List.of(items.get(0)), record.getItems());
        assertEquals(new BigDecimal("20.00"), record.getTotal());
    }
}
