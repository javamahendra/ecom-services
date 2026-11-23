package com.app.entity;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("pending_inventory_statuses")
public class PendingInventoryStatus {
    @Id private String orderId;
    private String status;
    private String message;
    private Instant occurredAt;
    public PendingInventoryStatus() {}
    public PendingInventoryStatus(String orderId, String status, String message, Instant occurredAt) {
        this.orderId = orderId; this.status = status; this.message = message; this.occurredAt = occurredAt;
    }
    public String getOrderId() { return orderId; }
    public String getStatus() { return status; }
    public String getMessage() { return message; }
    public Instant getOccurredAt() { return occurredAt; }
}
