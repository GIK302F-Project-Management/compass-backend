package com.compass.backend.dto;

import java.time.Instant;

import com.compass.backend.model.User;

/**
 * Public view of a user. It never includes the password or its hash.
 */
public record UserResponse(int id, String name, String email, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getCreatedAt());
    }

}
