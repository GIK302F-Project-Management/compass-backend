package com.compass.backend.exception;

/**
 * Thrown when an activity does not exist or belongs to a plan the student is not a member of.
 */
public class ActivityNotFoundException extends RuntimeException {

    public ActivityNotFoundException() {
        super("Activity not found.");
    }

}
