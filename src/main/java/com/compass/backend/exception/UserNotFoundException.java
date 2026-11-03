package com.compass.backend.exception;

/**
 * Thrown when no account exists for the given email.
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException() {
        super("Sign Up first!");
    }

}
