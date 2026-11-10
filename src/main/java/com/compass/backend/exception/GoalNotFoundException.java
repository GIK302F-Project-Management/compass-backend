package com.compass.backend.exception;

/**
 * Thrown when a goal does not exist or belongs to another student. Both cases look the same on
 * purpose, so a student cannot find out which goal ids other students have.
 */
public class GoalNotFoundException extends RuntimeException {

    public GoalNotFoundException() {
        super("Goal not found.");
    }

}
