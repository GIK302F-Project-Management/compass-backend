package com.compass.backend.dto;

import java.time.Instant;

import com.compass.backend.model.PlanInvitation;

/** An invitation link. The web page builds the link from the token, e.g. /join/{token}. */
public record InvitationResponse(int id, String token, Instant createdAt, Instant expiresAt) {

    public static InvitationResponse from(PlanInvitation invitation) {
        return new InvitationResponse(invitation.getId(), invitation.getToken(), invitation.getCreatedAt(),
            invitation.getExpiresAt());
    }

}
