package com.app.event;

public record StockSyncEvent(
        String eventType, String productId, String productCode, String variantId,
        String productName, String color, int quantity) {
    public StockSyncEvent(String eventType, String productId, String variantId,
                          String productName, String color, int quantity) {
        this(eventType, productId, null, variantId, productName, color, quantity);
    }

    public static StockSyncEvent update(String productId, String productCode, String variantId, String productName,
                                        String color, int quantity) {
        return new StockSyncEvent("STOCK_SET", productId, productCode, variantId, productName, color, quantity);
    }

    public static StockSyncEvent seed(String productId, String productCode, String variantId, String productName,
                                      String color, int quantity) {
        return new StockSyncEvent("STOCK_SEED", productId, productCode, variantId, productName, color, quantity);
    }

    public static StockSyncEvent reconcile(String productId, String productCode, String variantId, String productName,
                                           String color, int quantity) {
        return new StockSyncEvent(
                "STOCK_RECONCILE", productId, productCode, variantId, productName, color, quantity);
    }

    public static StockSyncEvent metadata(String productId, String productCode, String variantId, String productName,
                                          String color, int quantity) {
        return new StockSyncEvent(
                "STOCK_METADATA", productId, productCode, variantId, productName, color, quantity);
    }

    public static StockSyncEvent remove(String productId, String productCode, String variantId) {
        return new StockSyncEvent("STOCK_REMOVED", productId, productCode, variantId, null, null, 0);
    }

    public String key() {
        return productId + ":" + variantId;
    }
}
