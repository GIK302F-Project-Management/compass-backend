package com.compass.backend.dto;

import com.compass.backend.validation.MaxUtf8Bytes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /user/register. The rules mirror validateRegister() in the frontend
 * (compass/src/validation/register.ts), so the backend never trusts the browser alone.
 */
public record RegisterRequest(
    @NotBlank(message = "Please enter your name.")
    @Size(max = 100, message = "The name must have at most 100 characters.")
    String name,

    @NotBlank(message = "Please enter your university email.")
    @Pattern(
        regexp = "^[^\\s@]+@" + RegisterRequest.UNIVERSITY_EMAIL_DOMAIN_REGEX + "$",
        message = "Use your university email (name@" + RegisterRequest.UNIVERSITY_EMAIL_DOMAIN + ")."
    )
    String email,

    @NotNull(message = "Please enter a password.")
    @Size(min = 8, max = 72, message = "The password must have between 8 and 72 characters.")
    @MaxUtf8Bytes(value = 72, message = "The password is too long (accented letters and emojis count as more than one character).")
    String password
) {

    public static final String UNIVERSITY_EMAIL_DOMAIN = "alumni.mondragon.edu";
    private static final String UNIVERSITY_EMAIL_DOMAIN_REGEX = "alumni\\.mondragon\\.edu";

    /**
     * Trims the name and normalizes the email (trim + lowercase) before validation runs,
     * the same way the frontend does with normalizeEmail().
     */
    public RegisterRequest {
        name = name == null ? null : name.trim();
        email = email == null ? null : email.trim().toLowerCase();
    }

}
