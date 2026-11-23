package com.app.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import java.util.UUID;

public record ProductVariantRequest(
        UUID id,
        @NotBlank @Size(max = 100) String color,
        @NotEmpty @Size(max = 20) List<@Size(max = 2048) String> imageUrls,
        @PositiveOrZero int stockCount) {
    public ProductVariantRequest(String color, List<String> imageUrls, int stockCount) {
        this(null, color, imageUrls, stockCount);
    }
}
