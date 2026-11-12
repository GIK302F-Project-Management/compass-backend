package com.compass.backend.exception;

/**
 * Thrown when the owner of an activity tries to leave it.
 */
public class OwnerCannotLeaveException extends RuntimeException {

    public OwnerCannotLeaveException() {
        super("The owner cannot leave the activity. Delete it instead.");
    }

}
