package com.app.service;

import com.app.event.UserEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class UserNotificationListener {
    private static final Logger log = LoggerFactory.getLogger(UserNotificationListener.class);
    private final NotificationService notifications;
    public UserNotificationListener(NotificationService notifications) { this.notifications = notifications; }
    @KafkaListener(topics = "commerce.user-events", groupId = "notification-service")
    public void onUserEvent(UserEvent event) {
        try {
            notifications.processUser(event);
        } catch (RuntimeException exception) {
            log.error("Failed to process user notification event: eventId={}, type={}",
                    event.eventId(), event.eventType(), exception);
            throw exception;
        }
    }
}
