package com.compass.backend.exception;

/**
 * Thrown when the owner of a plan tries to leave it.
 */
public class OwnerCannotLeaveException extends RuntimeException {

    public OwnerCannotLeaveException() {
        super("The owner cannot leave the plan. Delete it instead.");
    }

}
