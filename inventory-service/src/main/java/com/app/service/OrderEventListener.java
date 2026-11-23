package com.app.service;

import com.app.event.OrderEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {
    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);
    private final InventoryService inventory;
    public OrderEventListener(InventoryService inventory) { this.inventory = inventory; }
    @KafkaListener(topics = "commerce.order-events", groupId = "inventory-service")
    public void onOrderEvent(OrderEvent event) {
        try { inventory.handle(event); }
        catch (RuntimeException exception) {
            log.error("Unable to process order event: eventId={}, orderId={}", event.eventId(), event.orderId(), exception);
            throw exception;
        }
    }
}
