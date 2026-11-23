package com.app.request;

import jakarta.validation.constraints.Positive;

public record UpdateCartQuantityRequest(@Positive int quantity) {}
