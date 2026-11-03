package com.compass.backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Body of POST /user/login. Only presence is checked: the rules for a good password belong to
 * registration, and a login must not reveal them.
 */
public record LoginRequest(
    @NotBlank(message = "Email is required.") String email,
    @NotBlank(message = "Password is required.") String password
) {

    public LoginRequest {
        email = email == null ? null : email.trim();
    }

    /** The password is hidden so it never ends up in a log line. */
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=***]";
    }

}
