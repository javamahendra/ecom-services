package com.app.service;

import com.app.event.StockSyncEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ProductStockEventListener {
    private static final Logger log = LoggerFactory.getLogger(ProductStockEventListener.class);
    private final InventoryService inventory;

    public ProductStockEventListener(InventoryService inventory) {
        this.inventory = inventory;
    }

    @KafkaListener(topics = "commerce.product-stock-events", groupId = "inventory-service")
    public void onStockChanged(StockSyncEvent event) {
        try {
            inventory.syncFromProduct(event);
        } catch (RuntimeException exception) {
            log.error("Failed to sync product stock to inventory: productId={}, variantId={}",
                    event.productId(), event.variantId(), exception);
            throw exception;
        }
    }
}
