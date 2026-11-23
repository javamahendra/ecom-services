package com.app.service;

import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DailyNotificationDelivery {
    private static final Logger log = LoggerFactory.getLogger(DailyNotificationDelivery.class);
    private final NotificationService notifications;
    private final ZoneId zone;

    public DailyNotificationDelivery(NotificationService notifications,
            @Value("${notification.delivery.zone:Asia/Kolkata}") String zone) {
        this.notifications = notifications;
        this.zone = ZoneId.of(zone);
    }

    @Scheduled(cron = "${notification.delivery.cron:0 0 9 * * *}",
            zone = "${notification.delivery.zone:Asia/Kolkata}")
    public void deliverDailyBatch() {
        LocalDate today = LocalDate.now(zone);
        int completed = notifications.deliverDue(today);
        log.info("Daily notification batch finished: date={}, completed={}", today, completed);
    }
}
