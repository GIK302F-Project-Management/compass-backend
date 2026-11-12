package com.compass.backend.dto;

import java.time.LocalDate;

import com.compass.backend.model.Activity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /activities and PUT /activities/{id}. The rules mirror
 * validateNewActivity() in the frontend, so the backend never trusts the browser alone.
 */
public record ActivityRequest(
    @NotBlank(message = "Please give the activity a name.")
    @Size(max = Activity.MAX_TITLE_LENGTH,
          message = "The name must have at most " + Activity.MAX_TITLE_LENGTH + " characters.")
    String title,

    @NotBlank(message = "Please enter the subject or area.")
    @Size(max = Activity.MAX_SUBJECT_LENGTH,
          message = "The subject must have at most " + Activity.MAX_SUBJECT_LENGTH + " characters.")
    String subject,

    @NotNull(message = "Please pick a due date.")
    LocalDate dueDate
) {

    /** Trims the texts before validation runs, so " " counts as empty. */
    public ActivityRequest {
        title = title == null ? null : title.trim();
        subject = subject == null ? null : subject.trim();
    }

}
