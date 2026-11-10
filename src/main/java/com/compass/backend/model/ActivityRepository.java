package com.compass.backend.model;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for activities. */
public interface ActivityRepository extends JpaRepository<Activity, Integer> {

    /** The activities of a plan, oldest first (new ones go at the end). */
    List<Activity> findByPlanIdOrderByIdAsc(int planId);

    void deleteByPlanId(int planId);

}
