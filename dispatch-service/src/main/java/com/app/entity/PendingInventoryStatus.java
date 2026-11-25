package com.app.entity;

import com.app.event.InventoryEvent;
import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("pending_inventory_statuses")
public class PendingInventoryStatus {
    @Id private String orderId;
    private String status;
    private String message;
    private Instant occurredAt;
    private List<InventoryEvent.ItemResult> itemResults;
    public PendingInventoryStatus() {}
    public PendingInventoryStatus(String orderId, String status, String message, Instant occurredAt,
                                  List<InventoryEvent.ItemResult> itemResults) {
        this.orderId = orderId; this.status = status; this.message = message; this.occurredAt = occurredAt;
        this.itemResults = itemResults;
    }
    public String getOrderId() { return orderId; }
    public String getStatus() { return status; }
    public String getMessage() { return message; }
    public Instant getOccurredAt() { return occurredAt; }
    public List<InventoryEvent.ItemResult> getItemResults() { return itemResults; }
}
