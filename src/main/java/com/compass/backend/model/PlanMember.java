package com.compass.backend.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * A student who can see and change a plan. The owner is a member too, so "is this student in the
 * plan?" is always one lookup. The database refuses a second row for the same student and plan.
 */
@Entity
@Table(name = "plan_members", uniqueConstraints = @UniqueConstraint(columnNames = { "plan_id", "user_id" }))
public class PlanMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "plan_id", nullable = false, updatable = false)
    private int planId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private int userId;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt = Instant.now();

    protected PlanMember() {
        // required by JPA
    }

    public PlanMember(int planId, int userId) {
        this.planId = planId;
        this.userId = userId;
    }

    public int getId() {
        return id;
    }

    public int getPlanId() {
        return planId;
    }

    public int getUserId() {
        return userId;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

}
