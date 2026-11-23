package com.app.response;

import com.app.entity.UserProfile;
public record UserResponse(String id, String username, String email, String firstName, String lastName, String phone) {

    public static UserResponse from(UserProfile profile) {
        return new UserResponse(
                profile.getId(),
                profile.getUsername(),
                profile.getEmail(),
                profile.getFirstName(),
                profile.getLastName(),
                profile.getPhone());
    }
}
