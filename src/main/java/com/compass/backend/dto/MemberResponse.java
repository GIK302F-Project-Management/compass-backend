package com.compass.backend.dto;

import java.time.Instant;

import com.compass.backend.service.SharingService.MemberView;

/** A member of an activity, by name only; {@code you} marks the logged-in student. */
public record MemberResponse(int userId, String name, boolean owner, boolean you, Instant joinedAt) {

    public static MemberResponse from(MemberView member, int userId) {
        return new MemberResponse(member.userId(), member.name(), member.owner(), member.userId() == userId,
            member.joinedAt());
    }

}
