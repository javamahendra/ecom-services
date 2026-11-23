package com.app.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record InventoryRequest(
        @NotBlank String productId,
        @NotBlank String variantId,
        @NotBlank @Size(max = 200) String productName,
        @NotBlank @Size(max = 100) String color,
        @PositiveOrZero int quantity) {}
