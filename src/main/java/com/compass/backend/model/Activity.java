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
 * Something to do for a subject, with a due date (an assignment, an exam to prepare...): one sheet of the
 * student's atlas. The student who created it is the owner; other students can join it through an
 * invitation (see ActivityMember) and then it is in each member's atlas, with one shared list of tasks.
 * The services always check that the student is a member before reading or changing it.
 */
@Entity
@Table(name = "study_activities", indexes = @Index(name = "idx_study_activities_owner_id", columnList = "owner_id"))
public class Activity {

    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_SUBJECT_LENGTH = 100;
    public static final int MAX_MEMBERS = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private int ownerId;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(nullable = false, length = MAX_SUBJECT_LENGTH)
    private String subject;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false)
    private boolean done;

    /** The member who completed it (null while it is open). */
    @Column(name = "done_by")
    private Integer doneBy;

    @Column(name = "created_by", nullable = false, updatable = false)
    private int createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Activity() {
        // required by JPA
    }

    /** A new activity, owned by the student who creates it. */
    public Activity(int ownerId, String title, String subject, LocalDate dueDate) {
        this.ownerId = ownerId;
        this.title = title;
        this.subject = subject;
        this.dueDate = dueDate;
        this.createdBy = ownerId;
    }

    /** Marks it done by the given member, or reopens it (done = false forgets who completed it). */
    public void setDone(boolean done, int userId) {
        this.done = done;
        this.doneBy = done ? userId : null;
    }

    public int getId() {
        return id;
    }

    public int getOwnerId() {
        return ownerId;
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
