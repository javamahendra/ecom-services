package com.app.request;

import jakarta.validation.constraints.NotBlank;

public record UserRoleRequest(@NotBlank String role) {}
