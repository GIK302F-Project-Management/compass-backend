package com.compass.backend.model;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data repository for tasks. */
public interface TaskRepository extends JpaRepository<Task, Integer> {

    /** The tasks of an activity, oldest first. */
    List<Task> findByActivityIdOrderByIdAsc(int activityId);

    /** Every task of every activity the student is a member of, oldest first (the route planner needs them all). */
    @Query("select t from Task t where t.activityId in "
        + "(select m.activityId from ActivityMember m where m.userId = :userId) order by t.id")
    List<Task> findForMember(@Param("userId") int userId);

    void deleteByActivityId(int activityId);

    /** Hands back the tasks of a student who left an activity, so the others can take them. */
    @Modifying
    @Query("update Task t set t.assigneeId = null where t.activityId = :activityId and t.assigneeId = :userId")
    int unassign(@Param("activityId") int activityId, @Param("userId") int userId);

}
