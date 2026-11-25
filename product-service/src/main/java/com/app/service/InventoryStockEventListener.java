package com.app.service;

import com.app.event.InventoryStockChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class InventoryStockEventListener {
    private static final Logger log = LoggerFactory.getLogger(InventoryStockEventListener.class);
    private final ProductService products;

    public InventoryStockEventListener(ProductService products) {
        this.products = products;
    }

    @KafkaListener(topics = "commerce.inventory-stock-events", groupId = "product-service")
    public void onStockChanged(InventoryStockChangedEvent event) {
        try {
            products.applyInventoryStock(event);
        } catch (RuntimeException exception) {
            log.error("Failed to reflect inventory stock in product catalog: productId={}, variantId={}",
                    event.productId(), event.variantId(), exception);
            throw exception;
        }
    }
}
