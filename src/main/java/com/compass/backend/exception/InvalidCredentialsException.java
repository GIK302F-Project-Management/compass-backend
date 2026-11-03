package com.compass.backend.exception;

/**
 * Thrown when a login fails. The same message is used for an unknown email and for a wrong password,
 * so the response never tells an attacker which emails have an account.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Incorrect email or password.");
    }

}
