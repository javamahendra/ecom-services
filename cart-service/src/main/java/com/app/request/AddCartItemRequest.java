package com.app.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record AddCartItemRequest(@NotBlank String productId, @NotBlank String variantId,
                                 @Positive int quantity) {}
