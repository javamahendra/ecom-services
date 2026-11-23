package com.app.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank @Size(max = 60) String label,
        @NotBlank @Size(max = 150) String recipientName,
        @NotBlank @Size(max = 30) String phone,
        @NotBlank @Size(max = 200) String line1,
        @Size(max = 200) String line2,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(max = 100) String region,
        @NotBlank @Size(max = 20) String postalCode,
        @NotBlank @Size(max = 100) String country,
        boolean defaultAddress) {}
