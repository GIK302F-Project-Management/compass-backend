package com.compass.backend.exception;

/**
 * Thrown when someone tries to join a plan that has reached its member limit.
 */
public class PlanFullException extends RuntimeException {

    public PlanFullException() {
        super("This plan already has the maximum number of members.");
    }

}
