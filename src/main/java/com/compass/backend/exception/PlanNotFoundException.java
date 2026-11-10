package com.compass.backend.exception;

/**
 * Thrown when a plan does not exist or the student is not a member of it. Both cases look the same on
 * purpose, so a student cannot find out which plan ids exist.
 */
public class PlanNotFoundException extends RuntimeException {

    public PlanNotFoundException() {
        super("Plan not found.");
    }

}
