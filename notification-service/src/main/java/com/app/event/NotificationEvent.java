package com.app.event;

import java.time.Instant;
import java.util.Map;

public record NotificationEvent(String eventId, String eventType, String userId, String email,
                                String phone, String orderId, Map<String, String> data,
                                Instant occurredAt) {}
