package com.compass.backend.model;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * One step of an activity. Its plan is the activity's plan (planId is kept here too, so a plan's
 * tasks come from one query and the progress needs no join).
 */
@Entity
@Table(name = "tasks", indexes = {
    @Index(name = "idx_tasks_activity_id", columnList = "activity_id"),
    @Index(name = "idx_tasks_plan_id", columnList = "plan_id")
})
public class Task {

    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_DETAILS_LENGTH = 1000;
    public static final int MAX_ESTIMATED_MINUTES = 10080; // one week, what the study planner accepts

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "activity_id", nullable = false, updatable = false)
    private int activityId;

    @Column(name = "plan_id", nullable = false, updatable = false)
    private int planId;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(nullable = false, length = MAX_DETAILS_LENGTH)
    private String details;

    /** Null when the task has no deadline of its own. */
    @Column(name = "due_date")
    private LocalDate dueDate;

    /** Null when nobody estimated how long it takes. */
    @Column(name = "estimated_minutes")
    private Integer estimatedMinutes;

    @Column(nullable = false)
    private boolean done;

    /** The member who completed it (null while it is open). */
    @Column(name = "done_by")
    private Integer doneBy;

    @Column(name = "created_by", nullable = false, updatable = false)
    private int createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Task() {
        // required by JPA
    }

    public Task(int activityId, int planId, String title, String details, LocalDate dueDate,
            Integer estimatedMinutes, int createdBy) {
        this.activityId = activityId;
        this.planId = planId;
        this.title = title;
        this.details = details;
        this.dueDate = dueDate;
        this.estimatedMinutes = estimatedMinutes;
        this.createdBy = createdBy;
    }

    /** Marks it done by the given member, or reopens it (done = false forgets who completed it). */
    public void setDone(boolean done, int userId) {
        this.done = done;
        this.doneBy = done ? userId : null;
    }

    public int getId() {
        return id;
    }

    public int getActivityId() {
        return activityId;
    }

    public int getPlanId() {
        return planId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public Integer getEstimatedMinutes() {
        return estimatedMinutes;
    }

    public void setEstimatedMinutes(Integer estimatedMinutes) {
        this.estimatedMinutes = estimatedMinutes;
    }

    public boolean isDone() {
        return done;
    }

    public Integer getDoneBy() {
        return doneBy;
    }

    public int getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

}
