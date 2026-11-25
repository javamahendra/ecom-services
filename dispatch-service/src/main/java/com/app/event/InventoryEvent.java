package com.app.event;

import java.time.Instant;
import java.util.List;

public record InventoryEvent(
        String eventId, String orderId, String userId, String email, String phone, String name,
        String status, String message, Instant occurredAt, List<ItemResult> itemResults) {
    public record ItemResult(int itemIndex, String status, String message) {}
}
