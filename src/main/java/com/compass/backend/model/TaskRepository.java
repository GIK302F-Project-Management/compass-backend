package com.compass.backend.model;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for tasks. */
public interface TaskRepository extends JpaRepository<Task, Integer> {

    /** The tasks of an activity, oldest first. */
    List<Task> findByActivityIdOrderByIdAsc(int activityId);

    /** Every task of a plan, oldest first. */
    List<Task> findByPlanIdOrderByIdAsc(int planId);

    void deleteByActivityId(int activityId);

    void deleteByPlanId(int planId);

}
