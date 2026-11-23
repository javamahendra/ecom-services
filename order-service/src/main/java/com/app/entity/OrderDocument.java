package com.app.entity;

import com.app.event.OrderEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("orders")
public class OrderDocument {
    @Id private String id;
    private String userId;
    private String email;
    private List<OrderEvent.OrderItem> items;
    private OrderEvent.ShippingAddress shippingAddress;
    private BigDecimal total;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
    public OrderDocument() {}
    public OrderDocument(String id, String userId, String email, List<OrderEvent.OrderItem> items,
                         OrderEvent.ShippingAddress address, BigDecimal total) {
        this.id = id; this.userId = userId; this.email = email; this.items = items;
        this.shippingAddress = address; this.total = total; this.status = "PENDING_INVENTORY";
        this.createdAt = Instant.now(); this.updatedAt = this.createdAt;
    }
    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getEmail() { return email; }
    public List<OrderEvent.OrderItem> getItems() { return items; }
    public OrderEvent.ShippingAddress getShippingAddress() { return shippingAddress; }
    public BigDecimal getTotal() { return total; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setStatus(String status) { this.status = status; this.updatedAt = Instant.now(); }
}
