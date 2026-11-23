package com.app.response;

import java.util.List;
import java.util.UUID;

public record ProductVariantResponse(
        UUID id, String color, List<String> imageUrls, boolean inStock, int stockCount) {}
