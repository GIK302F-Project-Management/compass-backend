package com.compass.backend.dto;

import java.time.Instant;

import com.compass.backend.model.Goal;

/**
 * Public view of a goal. The owner's id is not included: a student only ever sees their own goals.
 */
public record GoalResponse(int id, String title, String description, Instant createdAt) {

    public static GoalResponse from(Goal goal) {
        return new GoalResponse(goal.getId(), goal.getTitle(), goal.getDescription(), goal.getCreatedAt());
    }

}
