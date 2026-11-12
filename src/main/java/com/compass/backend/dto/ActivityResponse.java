package com.compass.backend.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.compass.backend.model.Activity;
import com.compass.backend.service.ActivityService.ActivityView;

/**
 * An activity as the logged-in student sees it. {@code role} is OWNER or MEMBER; {@code memberCount} above 1
 * means it is shared. {@code doneBy} is the id of the member who completed it.
 */
public record ActivityResponse(int id, int ownerId, String role, int memberCount, String title, String subject,
        LocalDate dueDate, boolean done, Integer doneBy, Instant createdAt) {

    public static ActivityResponse from(ActivityView view, int userId) {
        Activity activity = view.activity();
        return new ActivityResponse(activity.getId(), activity.getOwnerId(),
            activity.getOwnerId() == userId ? "OWNER" : "MEMBER", view.memberCount(), activity.getTitle(),
            activity.getSubject(), activity.getDueDate(), activity.isDone(), activity.getDoneBy(),
            activity.getCreatedAt());
    }

}
