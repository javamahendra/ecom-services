package com.app.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

public record VariantUpdateRequest(
        @NotBlank @Size(max = 100) String color,
        @NotEmpty @Size(max = 20) List<@Size(max = 2048) String> imageUrls,
        @PositiveOrZero @Max(Integer.MAX_VALUE) int stockCount) {}
