package com.app.service;

import com.app.event.InventoryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class InventoryNotificationListener {
    private static final Logger log = LoggerFactory.getLogger(InventoryNotificationListener.class);
    private final NotificationService notifications;
    public InventoryNotificationListener(NotificationService notifications) {
        this.notifications = notifications;
    }
    @KafkaListener(topics = "commerce.inventory-events", groupId = "notification-service")
    public void onInventoryEvent(InventoryEvent event) {
        notifications.processInventory(event);
        log.info("Inventory notification recorded: eventId={}, orderId={}, status={}",
                event.eventId(), event.orderId(), event.status());
    }
}
