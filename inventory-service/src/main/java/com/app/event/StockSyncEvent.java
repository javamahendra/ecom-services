package com.app.event;

public record StockSyncEvent(
        String eventType, String productId, String productCode, String variantId,
        String productName, String color, int quantity) {
    public StockSyncEvent(String eventType, String productId, String variantId,
                          String productName, String color, int quantity) {
        this(eventType, productId, null, variantId, productName, color, quantity);
    }

    public String key() {
        return productId + ":" + variantId;
    }
}
