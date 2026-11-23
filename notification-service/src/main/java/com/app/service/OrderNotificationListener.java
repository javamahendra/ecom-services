package com.app.service;

import com.app.event.OrderEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderNotificationListener {
    private static final Logger log = LoggerFactory.getLogger(OrderNotificationListener.class);
    private final NotificationService notifications;
    public OrderNotificationListener(NotificationService notifications) {
        this.notifications = notifications;
    }
    @KafkaListener(topics = "commerce.order-events", groupId = "notification-service")
    public void onOrderEvent(OrderEvent event) {
        notifications.processOrder(event);
        log.info("Order notification processed: eventId={}, orderId={}, type={}",
                event.eventId(), event.orderId(), event.eventType());
    }
}
