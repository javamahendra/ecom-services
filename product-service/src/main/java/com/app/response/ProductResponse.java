package com.app.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ProductResponse(
        String id,
        String productCode,
        String name,
        String description,
        String category,
        String productType,
        String baseColor,
        BigDecimal price,
        BigDecimal discount,
        BigDecimal offerPrice,
        LocalDate listDate,
        boolean inStock,
        int stockCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ProductVariantResponse> variants) {}
