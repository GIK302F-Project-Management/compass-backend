package com.compass.backend.exception;

/**
 * Thrown when a member who is not the owner tries to rename or delete the plan, manage its invitations or
 * remove other members.
 */
public class NotPlanOwnerException extends RuntimeException {

    public NotPlanOwnerException() {
        super("Only the owner of the plan can do this.");
    }

}
