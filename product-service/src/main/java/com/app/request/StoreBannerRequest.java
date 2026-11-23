package com.app.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StoreBannerRequest(
        boolean enabled,
        @NotBlank @Size(max = 100) String eyebrow,
        @NotBlank @Size(max = 160) String heading,
        @NotBlank @Size(max = 160) String accentText,
        @NotBlank @Size(max = 500) String description,
        @NotBlank @Size(max = 2048) String imageUrl,
        @NotBlank @Size(max = 300) String imageAlt,
        @NotBlank @Size(max = 80) String buttonText) {}
