package com.app.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public record UserRequest(
        @NotBlank @Size(min = 1, max = 100) String username,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 1, max = 100) String firstName,
        @NotBlank @Size(min = 1, max = 100) String lastName,
        @Pattern(regexp = "^$|^\\+?[0-9 ()-]{7,30}$") String phone) {
    public UserRequest(String username, String email, String firstName, String lastName) {
        this(username, email, firstName, lastName, null);
    }
}
