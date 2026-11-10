package com.compass.backend.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A study plan: a named set of activities that one or more students work on together. The student
 * who created it is the owner; the others joined through an invitation (see PlanMember). Every
 * student has at least one plan, a personal one created the first time they ask for their plans.
 */
@Entity
@Table(name = "plans")
public class Plan {

    public static final int MAX_NAME_LENGTH = 100;
    public static final int MAX_MEMBERS = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private int ownerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Plan() {
        // required by JPA
    }

    public Plan(String name, int ownerId) {
        this.name = name;
        this.ownerId = ownerId;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getOwnerId() {
        return ownerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

}
