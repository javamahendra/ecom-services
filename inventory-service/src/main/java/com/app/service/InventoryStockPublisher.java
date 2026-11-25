package com.app.service;

import com.app.entity.InventoryItem;
import com.app.event.InventoryStockChangedEvent;
import java.util.concurrent.CompletionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class InventoryStockPublisher {
    private static final Logger log = LoggerFactory.getLogger(InventoryStockPublisher.class);
    private final KafkaTemplate<Object, Object> kafka;

    public InventoryStockPublisher(KafkaTemplate<Object, Object> kafka) {
        this.kafka = kafka;
    }

    public void publish(InventoryItem item) {
        send(item);
    }

    public void publishAndWait(InventoryItem item) {
        try {
            send(item).join();
        } catch (CompletionException exception) {
            log.error("Failed to publish inventory stock update: productId={}, variantId={}",
                    item.getProductId(), item.getVariantId(), exception.getCause());
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not publish inventory stock to the product catalog. Try again when Kafka is available.",
                    exception.getCause());
        }
    }

    private java.util.concurrent.CompletableFuture<?> send(InventoryItem item) {
        InventoryStockChangedEvent event = new InventoryStockChangedEvent(
                item.getProductId(), item.getVariantId(), item.getProductName(), item.getColor(), item.getQuantity());
        return kafka.send("commerce.inventory-stock-events", item.getId(), event)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        log.error("Failed to publish inventory stock update: productId={}, variantId={}",
                                item.getProductId(), item.getVariantId(), error);
                    }
                });
    }
}
