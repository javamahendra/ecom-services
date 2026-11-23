package com.app.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ShoppingItem {
    private String productId;
    private String variantId;
    private String productName;
    private String category;
    private String productType;
    private String color;
    private BigDecimal unitPrice;
    private List<String> imageUrls = new ArrayList<>();
    private boolean inStock;
    private int availableStock;
    private int quantity;
    private Instant addedAt;
    private Instant updatedAt;

    public ShoppingItem() {}

    public ShoppingItem(String productId, String variantId, String productName, String category,
                        String productType, String color, BigDecimal unitPrice, List<String> imageUrls,
                        boolean inStock, int availableStock, int quantity) {
        this.productId = productId;
        this.variantId = variantId;
        this.productName = productName;
        this.category = category;
        this.productType = productType;
        this.color = color;
        this.unitPrice = unitPrice;
        this.imageUrls = new ArrayList<>(imageUrls);
        this.inStock = inStock;
        this.availableStock = availableStock;
        this.quantity = quantity;
        this.addedAt = Instant.now();
        this.updatedAt = this.addedAt;
    }

    public String getProductId() { return productId; }
    public String getVariantId() { return variantId; }
    public String getProductName() { return productName; }
    public String getCategory() { return category; }
    public String getProductType() { return productType; }
    public String getColor() { return color; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public List<String> getImageUrls() { return imageUrls; }
    public boolean isInStock() { return inStock; }
    public int getAvailableStock() { return availableStock; }
    public int getQuantity() { return quantity; }
    public Instant getAddedAt() { return addedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setQuantity(int quantity) { this.quantity = quantity; this.updatedAt = Instant.now(); }
    public void setSnapshot(String productName, String category, String productType, String color,
                            BigDecimal unitPrice, List<String> imageUrls,
                            boolean inStock, int availableStock) {
        this.productName = productName;
        this.category = category;
        this.productType = productType;
        this.color = color;
        this.unitPrice = unitPrice;
        this.imageUrls = new ArrayList<>(imageUrls);
        this.inStock = inStock;
        this.availableStock = availableStock;
        this.updatedAt = Instant.now();
    }
}
