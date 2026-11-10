package com.compass.backend.dto;

import java.time.LocalDate;

import com.compass.backend.model.Task;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /activities/{id}/tasks. The deadline and the estimate are optional. The rules mirror
 * validateTask() in the frontend. (Changing a task later uses PATCH /tasks/{id}, see TaskChanges.)
 */
public record TaskRequest(
    @NotBlank(message = "Please give the task a title.")
    @Size(max = Task.MAX_TITLE_LENGTH, message = "The title must have at most " + Task.MAX_TITLE_LENGTH + " characters.")
    String title,

    @Size(max = Task.MAX_DETAILS_LENGTH,
          message = "The details must have at most " + Task.MAX_DETAILS_LENGTH + " characters.")
    String details,

    LocalDate dueDate,

    @Min(value = 1, message = "The estimate must be at least 1 minute.")
    @Max(value = Task.MAX_ESTIMATED_MINUTES,
         message = "The estimate must be at most " + Task.MAX_ESTIMATED_MINUTES + " minutes.")
    Integer estimatedMinutes
) {

    /** Trims the texts before validation runs; the details are optional and stored as empty text. */
    public TaskRequest {
        title = title == null ? null : title.trim();
        details = details == null ? "" : details.trim();
    }

}
