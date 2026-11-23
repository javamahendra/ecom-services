package com.app.request;

import com.app.event.OrderEvent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record CreateOrderRequest(
        @Email @Size(max = 254) String email,
        @NotEmpty @Size(max = 100) List<@Valid Item> items,
        @NotNull @Valid Address shippingAddress) {
    public record Item(
            @NotBlank String productId,
            @NotBlank String variantId,
            @NotBlank @Size(max = 200) String productName,
            @Size(max = 2048) String imageUrl,
            @NotBlank @Size(max = 100) String color,
            @Positive int quantity,
            @NotNull @Positive BigDecimal unitPrice) {}
    public record Address(
            @NotBlank String recipientName,
            @NotBlank String phone,
            @NotBlank String line1,
            String line2,
            @NotBlank String city,
            @NotBlank String region,
            @NotBlank String postalCode,
            @NotBlank String country) {
        public OrderEvent.ShippingAddress toEventAddress() {
            return new OrderEvent.ShippingAddress(recipientName, phone, line1, line2,
                    city, region, postalCode, country);
        }
    }
    public List<OrderEvent.OrderItem> toEventItems() {
        return items.stream().map(i -> new OrderEvent.OrderItem(i.productId(), i.variantId(),
                i.productName(), i.imageUrl(), i.color(), i.quantity(), i.unitPrice())).toList();
    }
}
