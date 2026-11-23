package com.app.event;

import java.time.Instant;
import java.util.Map;

public record UserEvent(String eventId, String eventType, String userId, String email,
                        String phone, Map<String, String> data, Instant occurredAt) {}
