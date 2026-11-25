package com.app.entity;

import com.app.event.OrderEvent;
import com.app.event.InventoryEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("dispatches")
public class DispatchRecord {
    @Id private String orderId;
    private String userId;
    private String email;
    private List<OrderEvent.OrderItem> items;
    private OrderEvent.ShippingAddress shippingAddress;
    private BigDecimal total;
    private String status;
    private String trackingNumber;
    private List<StatusEntry> history = new ArrayList<>();
    private Instant createdAt;
    private Instant updatedAt;
    public DispatchRecord() {}
    public DispatchRecord(OrderEvent event) {
        orderId = event.orderId(); userId = event.userId(); email = event.email();
        items = event.items(); shippingAddress = event.shippingAddress(); total = event.total();
        status = "DISPATCH_SYNC".equals(event.eventType()) && event.status() != null ? switch (event.status()) {
            case "CONFIRMED" -> "READY_TO_DISPATCH";
            case "REJECTED" -> "INVENTORY_REJECTED";
            case "CANCELLED" -> "CANCELLED";
            default -> "AWAITING_INVENTORY";
        } : "AWAITING_INVENTORY";
        createdAt = event.occurredAt() == null ? Instant.now() : event.occurredAt();
        updatedAt = createdAt;
        history.add(new StatusEntry(status, "Order received", updatedAt));
    }
    public String getOrderId() { return orderId; }
    public String getUserId() { return userId; }
    public String getEmail() { return email; }
    public List<OrderEvent.OrderItem> getItems() { return items; }
    public OrderEvent.ShippingAddress getShippingAddress() { return shippingAddress; }
    public BigDecimal getTotal() { return total; }
    public String getStatus() { return status; }
    public String getTrackingNumber() { return trackingNumber; }
    public List<StatusEntry> getHistory() { return history; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void applyInventoryResult(String status, List<InventoryEvent.ItemResult> itemResults, String message) {
        if ("PARTIALLY_RESERVED".equals(status) && itemResults != null) {
            items = itemResults.stream()
                    .filter(result -> "RESERVED".equals(result.status()))
                    .map(InventoryEvent.ItemResult::itemIndex)
                    .filter(index -> index >= 0 && index < items.size())
                    .distinct()
                    .map(items::get)
                    .toList();
            total = items.stream()
                    .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        String dispatchStatus = switch (status) {
            case "RESERVED", "PARTIALLY_RESERVED" -> "READY_TO_DISPATCH";
            case "REJECTED" -> "INVENTORY_REJECTED";
            case "CANCELLED" -> "CANCELLED";
            default -> this.status;
        };
        if (dispatchStatus != null && !dispatchStatus.equals(this.status)) {
            updateStatus(dispatchStatus, null, message);
        }
    }

    public void updateStatus(String status, String trackingNumber, String note) {
        this.status = status;
        if (trackingNumber != null && !trackingNumber.isBlank()) this.trackingNumber = trackingNumber;
        this.updatedAt = Instant.now();
        history.add(new StatusEntry(status, note, updatedAt));
    }
    public record StatusEntry(String status, String note, Instant occurredAt) {}
}
