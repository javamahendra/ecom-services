package com.app.response;

import java.util.List;

public record AdminUserResponse(
        String id,
        String username,
        String email,
        String firstName,
        String lastName,
        String phone,
        List<String> roles,
        boolean enabled) {}
