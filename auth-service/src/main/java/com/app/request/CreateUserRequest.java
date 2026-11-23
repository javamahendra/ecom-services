package com.app.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Size(min = 12, max = 128) String password,
        @NotBlank @Size(min = 1, max = 100) String username,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 1, max = 100) String firstName,
        @NotBlank @Size(min = 1, max = 100) String lastName,
        @jakarta.validation.constraints.Pattern(regexp = "^$|^\\+?[0-9 ()-]{7,30}$") String phone) {

    public CreateUserRequest(String password, String username, String email, String firstName, String lastName) {
        this(password, username, email, firstName, lastName, null);
    }

    public UserRequest profile() {
        return new UserRequest(username, email, firstName, lastName, phone);
    }
}
