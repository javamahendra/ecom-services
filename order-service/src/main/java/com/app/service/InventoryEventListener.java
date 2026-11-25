package com.app.service;

import com.app.event.InventoryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class InventoryEventListener {
    private static final Logger log = LoggerFactory.getLogger(InventoryEventListener.class);
    private final OrderService orders;
    public InventoryEventListener(OrderService orders) { this.orders = orders; }
    @KafkaListener(topics = "commerce.inventory-events", groupId = "order-service")
    public void onInventoryEvent(InventoryEvent event) {
        String status = switch (event.status()) {
            case "RESERVED" -> "CONFIRMED";
            case "PARTIALLY_RESERVED" -> "PARTIALLY_CONFIRMED";
            case "REJECTED" -> "REJECTED";
            case "CANCELLED" -> "CANCELLED";
            default -> null;
        };
        if (status != null) orders.updateStatus(event.orderId(), status, event.itemResults());
        log.info("Inventory result received: orderId={}, status={}", event.orderId(), event.status());
    }
}
