package com.compass.backend.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.compass.backend.model.Task;

/**
 * A task. {@code assigneeId} is the member who will do it (null while nobody took it); {@code doneBy} is the
 * member who completed it. The names come from GET /activities/{id}/members.
 */
public record TaskResponse(int id, int activityId, String title, String details, LocalDate dueDate,
        Integer estimatedMinutes, Integer assigneeId, boolean done, Integer doneBy, Instant createdAt) {

    public static TaskResponse from(Task task) {
        return new TaskResponse(task.getId(), task.getActivityId(), task.getTitle(), task.getDetails(),
            task.getDueDate(), task.getEstimatedMinutes(), task.getAssigneeId(), task.isDone(), task.getDoneBy(),
            task.getCreatedAt());
    }

}
