package com.app.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "products")
public class Product {
    @Id
    private String id;
    private String name;
    private String description;
    private String baseColor;
    private String category;
    private String productType;
    private BigDecimal price;
    private BigDecimal discount;
    private BigDecimal offerPrice;
    private LocalDate listDate;
    private boolean inStock;
    private int stockCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ProductVariant> variants = new ArrayList<>();

    protected Product() {
        this.id= UUID.randomUUID().toString();
    }

    public Product(String name, String description, String category, String productType,
                   String baseColor, BigDecimal price,
                   BigDecimal discount, BigDecimal offerPrice, LocalDate listDate,
                   List<ProductVariant> variants) {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
        update(name, description, category, productType, baseColor, price,
                discount, offerPrice, listDate, variants);
    }

    public void update(String name, String description, String category, String productType,
                       String baseColor, BigDecimal price,
                       BigDecimal discount, BigDecimal offerPrice, LocalDate listDate,
                       List<ProductVariant> variants) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.productType = productType;
        this.baseColor = baseColor;
        this.price = price;
        this.discount = discount;
        this.offerPrice = offerPrice;
        this.listDate = listDate;
        this.variants = new ArrayList<>(variants);
        this.stockCount = variants.stream().mapToInt(ProductVariant::getStockCount).sum();
        this.inStock = stockCount > 0;
        this.updatedAt = LocalDateTime.now();
    }

    public void addVariant(ProductVariant variant) {
        variants.add(variant);
        recalculateStock();
    }

    public ProductVariant updateVariant(UUID variantId, String color,
                                        List<String> imageUrls, int stockCount) {
        ProductVariant variant = findVariant(variantId);
        variant.update(color, imageUrls, stockCount);
        recalculateStock();
        this.updatedAt = LocalDateTime.now();
        return variant;
    }

    public ProductVariant updateVariantStock(UUID variantId, int stockCount) {
        ProductVariant variant = findVariant(variantId);
        variant.updateStock(stockCount);
        recalculateStock();
        this.updatedAt = LocalDateTime.now();
        return variant;
    }

    public ProductVariant updateVariantImages(UUID variantId, List<String> imageUrls) {
        ProductVariant variant = findVariant(variantId);
        variant.updateImages(imageUrls);
        this.updatedAt = LocalDateTime.now();
        return variant;
    }

    public void deleteVariant(UUID variantId) {
        if (!variants.removeIf(variant -> variant.getId().equals(variantId.toString()))) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "Product variant not found: " + variantId);
        }
        recalculateStock();
        this.updatedAt = LocalDateTime.now();
    }

    private ProductVariant findVariant(UUID variantId) {
        return variants.stream()
                .filter(variant -> variant.getId().equals(variantId.toString()))
                .findFirst()
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "Product variant not found: " + variantId));
    }

    private void recalculateStock() {
        this.stockCount = variants.stream().mapToInt(ProductVariant::getStockCount).sum();
        this.inStock = stockCount > 0;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getBaseColor() { return baseColor; }
    public String getCategory() { return category; }
    public String getProductType() { return productType; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getDiscount() { return discount; }
    public BigDecimal getOfferPrice() { return offerPrice; }
    public LocalDate getListDate() { return listDate; }
    public boolean isInStock() { return inStock; }
    public int getStockCount() { return stockCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public List<ProductVariant> getVariants() { return variants; }
}
