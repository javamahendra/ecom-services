package com.app.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderEvent(
        String eventId, String eventType, String orderId, String userId, String email,
        List<OrderItem> items, ShippingAddress shippingAddress, BigDecimal total, Instant occurredAt) {
    public record OrderItem(String productId, String variantId, String productName,
                            String color, int quantity, BigDecimal unitPrice) {}
    public record ShippingAddress(String recipientName, String phone, String line1, String line2,
                                  String city, String region, String postalCode, String country) {}
}
