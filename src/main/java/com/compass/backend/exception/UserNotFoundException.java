package com.compass.backend.exception;

/**
 * Thrown when a valid token points to an account that no longer exists.
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException() {
        super("This account no longer exists.");
    }

}
