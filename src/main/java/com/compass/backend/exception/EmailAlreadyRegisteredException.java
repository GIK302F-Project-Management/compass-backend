package com.compass.backend.exception;

/**
 * Thrown when someone tries to register an email that already has an account.
 */
public class EmailAlreadyRegisteredException extends RuntimeException {

    public static final String MESSAGE = "An account with this email already exists.";

    public EmailAlreadyRegisteredException() {
        super(MESSAGE);
    }

    public EmailAlreadyRegisteredException(Throwable cause) {
        super(MESSAGE, cause);
    }

}
