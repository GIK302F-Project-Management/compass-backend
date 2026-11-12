package com.compass.backend.exception;

/**
 * Thrown when a member who is not the owner tries to delete the activity, manage its invitations or
 * remove other members.
 */
public class NotActivityOwnerException extends RuntimeException {

    public NotActivityOwnerException() {
        super("Only the owner of the activity can do this.");
    }

}
