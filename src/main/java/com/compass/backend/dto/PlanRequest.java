package com.compass.backend.dto;

import com.compass.backend.model.Plan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of POST /plans and PUT /plans/{id}: just the name. */
public record PlanRequest(
    @NotBlank(message = "Please give the plan a name.")
    @Size(max = Plan.MAX_NAME_LENGTH, message = "The name must have at most " + Plan.MAX_NAME_LENGTH + " characters.")
    String name
) {

    /** Trims the name before validation runs, so " " counts as empty. */
    public PlanRequest {
        name = name == null ? null : name.trim();
    }

}
