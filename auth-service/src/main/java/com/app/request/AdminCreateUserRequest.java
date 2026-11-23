package com.app.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminCreateUserRequest(
        @NotBlank @Size(min = 12, max = 128) String password,
        @NotNull @Valid UserRequest profile,
        @NotBlank String role) {}
