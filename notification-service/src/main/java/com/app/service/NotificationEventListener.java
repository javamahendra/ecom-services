package com.app.service;

import com.app.event.NotificationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventListener {
    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);
    private final NotificationService notifications;
    public NotificationEventListener(NotificationService notifications) { this.notifications = notifications; }
    @KafkaListener(topics = "commerce.notification-events", groupId = "notification-service")
    public void onNotificationEvent(NotificationEvent event) {
        try {
            notifications.process(event);
        } catch (RuntimeException exception) {
            log.error("Failed to process notification event: eventId={}, type={}",
                    event.eventId(), event.eventType(), exception);
            throw exception;
        }
    }
}
