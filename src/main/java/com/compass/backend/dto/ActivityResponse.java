package com.compass.backend.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.compass.backend.model.Activity;

/** An activity. {@code doneBy} is the id of the member who completed it (see the plan's members for the name). */
public record ActivityResponse(int id, int planId, String title, String subject, LocalDate dueDate, boolean done,
        Integer doneBy, Instant createdAt) {

    public static ActivityResponse from(Activity activity) {
        return new ActivityResponse(activity.getId(), activity.getPlanId(), activity.getTitle(),
            activity.getSubject(), activity.getDueDate(), activity.isDone(), activity.getDoneBy(),
            activity.getCreatedAt());
    }

}
