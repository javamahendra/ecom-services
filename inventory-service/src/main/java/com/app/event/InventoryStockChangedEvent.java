package com.app.event;

public record InventoryStockChangedEvent(
        String productId, String variantId, String productName, String color, int quantity) {}
