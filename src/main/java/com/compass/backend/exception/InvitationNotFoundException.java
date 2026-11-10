package com.compass.backend.exception;

/**
 * Thrown when an invitation token is unknown, expired or revoked. The three cases look the same on purpose.
 */
public class InvitationNotFoundException extends RuntimeException {

    public InvitationNotFoundException() {
        super("This invitation is not valid. It may have expired or been revoked.");
    }

}
