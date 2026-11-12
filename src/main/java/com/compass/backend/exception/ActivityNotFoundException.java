package com.compass.backend.exception;

/**
 * Thrown when an activity does not exist or the student is not one of its members. Both cases look the same on purpose..
 */
public class ActivityNotFoundException extends RuntimeException {

    public ActivityNotFoundException() {
        super("Activity not found.");
    }

}
