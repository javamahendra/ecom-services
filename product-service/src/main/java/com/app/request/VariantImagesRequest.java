package com.app.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record VariantImagesRequest(
        @NotEmpty @Size(max = 20) List<@Size(max = 2048) String> imageUrls) {}
