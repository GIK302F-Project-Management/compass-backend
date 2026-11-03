package com.compass.backend.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * BCrypt helper so passwords are never stored or compared in plain text.
 */
public final class PasswordHasher {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private PasswordHasher() {
    }

    /**
     * @brief This method hashes a raw password with BCrypt (random salt included).
     * @param rawPassword password typed by the user.
     * @return the BCrypt hash to be stored.
     */
    public static String hash(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }

    /**
     * @brief This method checks a raw password against a stored hash.
     * @param rawPassword password typed by the user.
     * @param hash stored BCrypt hash.
     * @return true when the password matches the hash.
     */
    public static boolean matches(String rawPassword, String hash) {
        return rawPassword != null && hash != null && ENCODER.matches(rawPassword, hash);
    }

}
