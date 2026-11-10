package com.compass.backend.dto;

import java.time.Instant;

import com.compass.backend.service.PlanService.PlanSummary;

/** A plan as the logged-in student sees it. {@code role} is OWNER or MEMBER. */
public record PlanResponse(int id, String name, String role, int memberCount, Instant createdAt) {

    public static PlanResponse from(PlanSummary summary, int userId) {
        return new PlanResponse(summary.plan().getId(), summary.plan().getName(),
            role(summary.plan().getOwnerId(), userId), summary.memberCount(), summary.plan().getCreatedAt());
    }

    static String role(int ownerId, int userId) {
        return ownerId == userId ? "OWNER" : "MEMBER";
    }

}
