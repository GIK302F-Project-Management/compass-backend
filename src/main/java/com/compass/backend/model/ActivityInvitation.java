package com.compass.backend.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * A link that lets other students join an activity. It works for several students until it expires
 * or the owner revokes it. Only a random token goes in the link, never the activity id.
 */
@Entity
@Table(name = "activity_invitations",
    indexes = @Index(name = "idx_activity_invitations_activity_id", columnList = "activity_id"))
public class ActivityInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "activity_id", nullable = false, updatable = false)
    private int activityId;

    @Column(nullable = false, unique = true, updatable = false)
    private String token;

    @Column(name = "created_by", nullable = false, updatable = false)
    private int createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected ActivityInvitation() {
        // required by JPA
    }

    public ActivityInvitation(int activityId, String token, int createdBy, Instant expiresAt) {
        this.activityId = activityId;
        this.token = token;
        this.createdBy = createdBy;
        this.expiresAt = expiresAt;
    }

    /** An invitation works while it is neither revoked nor expired. */
    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public void revoke() {
        this.revokedAt = Instant.now();
    }

    public int getId() {
        return id;
    }

    public int getActivityId() {
        return activityId;
    }

    public String getToken() {
        return token;
    }

    public int getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

}
