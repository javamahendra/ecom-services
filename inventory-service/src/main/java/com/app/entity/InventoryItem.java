package com.app.entity;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("inventory")
public class InventoryItem {
    @Id private String id;
    private String productId;
    private String variantId;
    private String productName;
    private String color;
    private int quantity;
    private Instant updatedAt;
    public InventoryItem() {}
    public InventoryItem(String productId, String variantId, String productName, String color, int quantity) {
        this.id = productId + ":" + variantId;
        this.productId = productId; this.variantId = variantId; this.productName = productName;
        this.color = color; this.quantity = quantity; this.updatedAt = Instant.now();
    }
    public String getId() { return id; }
    public String getProductId() { return productId; }
    public String getVariantId() { return variantId; }
    public String getProductName() { return productName; }
    public String getColor() { return color; }
    public int getQuantity() { return quantity; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void update(String name, String color, int quantity) {
        this.productName = name; this.color = color; this.quantity = quantity; this.updatedAt = Instant.now();
    }
}
