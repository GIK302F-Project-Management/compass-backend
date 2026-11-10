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
 * Something to do for a subject, with a due date (an assignment, an exam to prepare...). It belongs
 * to a plan, so every member of the plan sees it. The service always checks that the student is a
 * member of the activity's plan before reading or changing it.
 */
@Entity
@Table(name = "activities", indexes = @Index(name = "idx_activities_plan_id", columnList = "plan_id"))
public class Activity {

    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_SUBJECT_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "plan_id", nullable = false, updatable = false)
    private int planId;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(nullable = false, length = MAX_SUBJECT_LENGTH)
    private String subject;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false)
    private boolean done;

    /** The member who completed it (null while it is open); the plan's progress credits them. */
    @Column(name = "done_by")
    private Integer doneBy;

    @Column(name = "created_by", nullable = false, updatable = false)
    private int createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Activity() {
        // required by JPA
    }

    public Activity(int planId, String title, String subject, LocalDate dueDate, int createdBy) {
        this.planId = planId;
        this.title = title;
        this.subject = subject;
        this.dueDate = dueDate;
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

    public int getPlanId() {
        return planId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
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
