package com.app.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("notifications")
public class NotificationRecord {
    @Id private String id;
    private String eventId;
    private String orderId;
    private String userId;
    private String recipient;
    private String phone;
    private String type;
    private String message;
    private Map<String, String> data;
    private String emailStatus;
    private String emailError;
    private String smsStatus;
    private String smsError;
    private Instant createdAt;
    private LocalDate deliveryDate;
    private String deliveryStatus;
    private Instant deliveredAt;
    public NotificationRecord() {}
    public NotificationRecord(String eventId, String orderId, String userId, String recipient,
                              String type, String message) {
        this(eventId, orderId, userId, recipient, null, type, message, Map.of());
    }
    public NotificationRecord(String eventId, String orderId, String userId, String recipient,
                              String phone, String type, String message, Map<String, String> data) {
        this(eventId, orderId, userId, recipient, phone, type, message, data, LocalDate.now());
    }
    public NotificationRecord(String eventId, String orderId, String userId, String recipient,
                              String phone, String type, String message, Map<String, String> data,
                              LocalDate deliveryDate) {
        this.id = eventId; this.eventId = eventId; this.orderId = orderId; this.userId = userId;
        this.recipient = recipient; this.phone = phone; this.type = type; this.message = message;
        this.data = data; this.createdAt = Instant.now();
        this.deliveryDate = deliveryDate;
        this.deliveryStatus = "PENDING";
        this.emailStatus = "PENDING";
        this.smsStatus = "PENDING";
    }
    public String getId() { return id; }
    public String getEventId() { return eventId; }
    public String getOrderId() { return orderId; }
    public String getUserId() { return userId; }
    public String getRecipient() { return recipient; }
    public String getPhone() { return phone; }
    public String getType() { return type; }
    public String getMessage() { return message; }
    public Map<String, String> getData() { return data; }
    public Instant getCreatedAt() { return createdAt; }
    public LocalDate getDeliveryDate() { return deliveryDate; }
    public String getDeliveryStatus() { return deliveryStatus; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public String getEmailStatus() { return emailStatus; }
    public String getEmailError() { return emailError; }
    public String getSmsStatus() { return smsStatus; }
    public String getSmsError() { return smsError; }
    public void updateEmail(String status, String error) {
        emailStatus = status; emailError = error;
    }
    public void updateSms(String status, String error) {
        smsStatus = status; smsError = error;
    }
    public void retrySkippedChannels() {
        if ("SKIPPED".equals(emailStatus)) updateEmail("PENDING", null);
        if ("SKIPPED".equals(smsStatus)) updateSms("PENDING", null);
    }
    public void startDelivery() { deliveryStatus = "PROCESSING"; }
    public void finishDelivery(boolean failed) {
        deliveryStatus = failed ? "FAILED" : "COMPLETED";
        deliveredAt = failed ? null : Instant.now();
    }
    public void scheduleFor(LocalDate date) {
        deliveryDate = date;
        deliveryStatus = "PENDING";
        deliveredAt = null;
    }
}
