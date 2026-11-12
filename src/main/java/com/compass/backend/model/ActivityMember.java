package com.compass.backend.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** A student who can see and work on an activity. The owner is a member too, from the moment they create it. */
@Entity
@Table(name = "activity_members",
    uniqueConstraints = @UniqueConstraint(columnNames = { "activity_id", "user_id" }),
    indexes = @Index(name = "idx_activity_members_user_id", columnList = "user_id"))
public class ActivityMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "activity_id", nullable = false, updatable = false)
    private int activityId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private int userId;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt = Instant.now();

    protected ActivityMember() {
        // required by JPA
    }

    public ActivityMember(int activityId, int userId) {
        this.activityId = activityId;
        this.userId = userId;
    }

    public int getId() {
        return id;
    }

    public int getActivityId() {
        return activityId;
    }

    public int getUserId() {
        return userId;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

}
