package com.app.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ProductRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 4000) String description,
        @NotBlank @Size(max = 100) String category,
        @NotBlank @Size(max = 100) String productType,
        @NotBlank @Size(max = 100) String baseColor,
        @NotNull @DecimalMin("0.01") BigDecimal price,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal discount,
        @NotNull @DecimalMin("0") BigDecimal offerPrice,
        @NotNull LocalDate listDate,
        @Valid @Size(max = 50) List<ProductVariantRequest> variants) {}
