package com.compass.backend.exception;

/**
 * Thrown when a task does not exist or belongs to an activity the student is not a member of.
 */
public class TaskNotFoundException extends RuntimeException {

    public TaskNotFoundException() {
        super("Task not found.");
    }

}
