package com.app.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("shopping_lists")
@CompoundIndex(name = "user_id_unique", def = "{'userId': 1}", unique = true)
public class ShoppingList {
    @Id
    private String id;
    private String userId;
    private List<ShoppingItem> cartItems = new ArrayList<>();
    private List<ShoppingItem> wishlistItems = new ArrayList<>();
    private Instant createdAt;
    private Instant updatedAt;

    public ShoppingList() {}

    public ShoppingList(String userId) {
        this.userId = userId;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public List<ShoppingItem> getCartItems() { return cartItems; }
    public List<ShoppingItem> getWishlistItems() { return wishlistItems; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setCartItems(List<ShoppingItem> items) { this.cartItems = items; touch(); }
    public void setWishlistItems(List<ShoppingItem> items) { this.wishlistItems = items; touch(); }
    public void touch() { this.updatedAt = Instant.now(); }
}
