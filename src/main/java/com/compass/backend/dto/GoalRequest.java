package com.compass.backend.dto;

import com.compass.backend.model.Goal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /goals and PUT /goals/{id}. The rules mirror validateGoal() in the frontend
 * (compass/src/validation/goal.ts), so the backend never trusts the browser alone.
 * The description is optional: leaving it out stores an empty one.
 */
public record GoalRequest(
    @NotBlank(message = "Please give the goal a title.")
    @Size(max = Goal.MAX_TITLE_LENGTH, message = "The title must have at most " + Goal.MAX_TITLE_LENGTH + " characters.")
    String title,

    @Size(max = Goal.MAX_DESCRIPTION_LENGTH,
          message = "The description must have at most " + Goal.MAX_DESCRIPTION_LENGTH + " characters.")
    String description
) {

    /** Trims both texts before validation runs, so " " counts as empty. */
    public GoalRequest {
        title = title == null ? null : title.trim();
        description = description == null ? "" : description.trim();
    }

}
