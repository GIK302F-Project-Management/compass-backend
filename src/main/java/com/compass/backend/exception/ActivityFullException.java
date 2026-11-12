package com.compass.backend.exception;

/**
 * Thrown when someone tries to join an activity that has reached its member limit.
 */
public class ActivityFullException extends RuntimeException {

    public ActivityFullException() {
        super("This activity already has the maximum number of members.");
    }

}
