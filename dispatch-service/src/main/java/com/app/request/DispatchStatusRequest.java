package com.app.request;

import jakarta.validation.constraints.NotBlank;

public record DispatchStatusRequest(@NotBlank String status, String trackingNumber, String note) {}
