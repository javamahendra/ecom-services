package com.app.entity;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("processed_orders")
public class ProcessedOrder {
    @Id private String orderId;
    private String status;
    private List<Reservation> reservations;
    private Instant updatedAt;
    public ProcessedOrder() {}
    public ProcessedOrder(String orderId, String status, List<Reservation> reservations) {
        this.orderId = orderId; this.status = status; this.reservations = reservations; this.updatedAt = Instant.now();
    }
    public String getOrderId() { return orderId; }
    public String getStatus() { return status; }
    public List<Reservation> getReservations() { return reservations; }
    public void setState(String status) { this.status = status; this.updatedAt = Instant.now(); }
    public record Reservation(String inventoryId, int quantity) {}
}
