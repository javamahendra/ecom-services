package com.app.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ProductVariant {
    private String id;
    private String color;
    private List<String> imageUrls = new ArrayList<>();
    private int stockCount;
    private boolean inStock;

    protected ProductVariant() {}

    public ProductVariant(String color, List<String> imageUrls, int stockCount) {
        this(UUID.randomUUID(), color, imageUrls, stockCount);
    }

    public ProductVariant(UUID id, String color, List<String> imageUrls, int stockCount) {
        this.id = id.toString();
        this.color = color;
        this.imageUrls = new ArrayList<>(imageUrls);
        this.stockCount = stockCount;
        this.inStock = stockCount > 0;
    }

    public String getId() { return id; }
    public String getColor() { return color; }
    public List<String> getImageUrls() { return imageUrls; }
    public int getStockCount() { return stockCount; }
    public boolean isInStock() { return inStock; }

    public void updateStock(int stockCount) {
        this.stockCount = stockCount;
        this.inStock = stockCount > 0;
    }

    public void update(String color, List<String> imageUrls, int stockCount) {
        this.color = color;
        this.imageUrls = new ArrayList<>(imageUrls);
        updateStock(stockCount);
    }

    public void updateImages(List<String> imageUrls) {
        this.imageUrls = new ArrayList<>(imageUrls);
    }
}
