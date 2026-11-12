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
 * One step of an activity. Every member of the activity sees it; the assignee is the member who will do
 * it, so it goes into their study route (null while nobody took it).
 */
@Entity
@Table(name = "activity_tasks", indexes = {
    @Index(name = "idx_activity_tasks_activity_id", columnList = "activity_id"),
    @Index(name = "idx_activity_tasks_assignee_id", columnList = "assignee_id")
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

    /** The member who will do it; null while nobody took it. */
    @Column(name = "assignee_id")
    private Integer assigneeId;

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

    /** A new task, assigned to the student who creates it. */
    public Task(int activityId, String title, String details, LocalDate dueDate, Integer estimatedMinutes,
            int createdBy) {
        this.activityId = activityId;
        this.title = title;
        this.details = details;
        this.dueDate = dueDate;
        this.estimatedMinutes = estimatedMinutes;
        this.createdBy = createdBy;
        this.assigneeId = createdBy;
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

    public Integer getAssigneeId() {
        return assigneeId;
    }

    public void setAssigneeId(Integer assigneeId) {
        this.assigneeId = assigneeId;
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
