package com.compass.backend.dto;

import java.time.Instant;
import java.util.List;

import com.compass.backend.service.PlanService.PlanDetails;

/**
 * A plan with its members. Members are shown by name only; {@code you} marks the logged-in student.
 */
public record PlanDetailResponse(int id, String name, String role, Instant createdAt, List<Member> members) {

    public record Member(int userId, String name, boolean owner, boolean you, Instant joinedAt) {
    }

    public static PlanDetailResponse from(PlanDetails details, int userId) {
        List<Member> members = details.members().stream()
            .map(member -> new Member(member.userId(), member.name(), member.owner(), member.userId() == userId,
                member.joinedAt()))
            .toList();
        return new PlanDetailResponse(details.plan().getId(), details.plan().getName(),
            PlanResponse.role(details.plan().getOwnerId(), userId), details.plan().getCreatedAt(), members);
    }

}
