package com.app.request;

import jakarta.validation.constraints.NotBlank;

public record AddWishlistItemRequest(@NotBlank String productId, @NotBlank String variantId) {}
