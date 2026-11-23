package com.app.service;

import com.app.event.InventoryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class InventoryDispatchListener {
    private static final Logger log = LoggerFactory.getLogger(InventoryDispatchListener.class);
    private final DispatchService dispatch;
    public InventoryDispatchListener(DispatchService dispatch) { this.dispatch = dispatch; }
    @KafkaListener(topics = "commerce.inventory-events", groupId = "dispatch-service")
    public void onInventoryEvent(InventoryEvent event) {
        try { dispatch.inventoryEvent(event); }
        catch (RuntimeException exception) {
            log.error("Failed to process inventory dispatch event: orderId={}", event.orderId(), exception);
            throw exception;
        }
    }
}
