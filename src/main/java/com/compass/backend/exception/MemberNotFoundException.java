package com.compass.backend.exception;

/**
 * Thrown when the student to remove is not a member of the activity.
 */
public class MemberNotFoundException extends RuntimeException {

    public MemberNotFoundException() {
        super("Member not found.");
    }

}
