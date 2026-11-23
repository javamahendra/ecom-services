package com.app.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;

public record StockUpdateRequest(
        @PositiveOrZero @Max(Integer.MAX_VALUE) int stockCount) {}
