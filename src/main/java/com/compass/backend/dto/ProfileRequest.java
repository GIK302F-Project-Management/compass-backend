package com.compass.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of PUT /user/me: what a student can change in their profile. The email is how they log in, so it
 * stays as it is. The name rules are the same as at registration (RegisterRequest).
 */
public record ProfileRequest(
    @NotBlank(message = "Please enter your name.")
    @Size(max = 100, message = "The name must have at most 100 characters.")
    String name
) {

    /** Trims the name before validation runs, so " " counts as empty. */
    public ProfileRequest {
        name = name == null ? null : name.trim();
    }

}
