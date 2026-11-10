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
 * An invitation link to a plan. Whoever has the token and is logged in can join, until the
 * invitation expires or the owner revokes it. The token is long and random, so it cannot be guessed.
 */
@Entity
@Table(name = "plan_invitations", indexes = @Index(name = "idx_plan_invitations_plan_id", columnList = "plan_id"))
public class PlanInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "plan_id", nullable = false, updatable = false)
    private int planId;

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

    protected PlanInvitation() {
        // required by JPA
    }

    public PlanInvitation(int planId, String token, int createdBy, Instant expiresAt) {
        this.planId = planId;
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

    public int getPlanId() {
        return planId;
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
