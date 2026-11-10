package com.compass.backend.exception;

import java.util.Map;

/**
 * Thrown when the body of PATCH /tasks/{id} has a field that is unknown or invalid. Carries one
 * message per field, answered as {"errors": {"title": "..."}} like every other validation failure.
 */
public class InvalidTaskChangesException extends RuntimeException {

    private final Map<String, String> errors;

    public InvalidTaskChangesException(Map<String, String> errors) {
        super("Invalid task changes.");
        this.errors = errors;
    }

    public Map<String, String> getErrors() {
        return errors;
    }

}
