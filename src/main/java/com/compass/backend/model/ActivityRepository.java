package com.compass.backend.model;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data repository for activities. Who may see an activity is decided through ActivityMemberRepository. */
public interface ActivityRepository extends JpaRepository<Activity, Integer> {

    /** The activities a student is a member of (their own and the shared ones), oldest first. */
    @Query("select a from Activity a where a.id in "
        + "(select m.activityId from ActivityMember m where m.userId = :userId) order by a.id")
    List<Activity> findForMember(@Param("userId") int userId);

}
