package com.compass.backend.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.compass.backend.model.Task;

/** A task. {@code doneBy} is the id of the member who completed it. */
public record TaskResponse(int id, int activityId, int planId, String title, String details, LocalDate dueDate,
        Integer estimatedMinutes, boolean done, Integer doneBy, Instant createdAt) {

    public static TaskResponse from(Task task) {
        return new TaskResponse(task.getId(), task.getActivityId(), task.getPlanId(), task.getTitle(),
            task.getDetails(), task.getDueDate(), task.getEstimatedMinutes(), task.isDone(), task.getDoneBy(),
            task.getCreatedAt());
    }

}
