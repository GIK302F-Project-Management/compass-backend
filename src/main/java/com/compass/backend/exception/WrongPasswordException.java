package com.compass.backend.exception;

/**
 * Thrown when the password does not match the stored hash.
 */
public class WrongPasswordException extends RuntimeException {

    public WrongPasswordException() {
        super("Incorrect password!");
    }

}
