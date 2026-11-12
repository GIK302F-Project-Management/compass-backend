package com.compass.backend.model;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for activity memberships. */
public interface ActivityMemberRepository extends JpaRepository<ActivityMember, Integer> {

    /** The members of an activity, owner first (the owner joins when the activity is created). */
    List<ActivityMember> findByActivityIdOrderByIdAsc(int activityId);

    /** The memberships of several activities at once (to count the members of a whole atlas). */
    List<ActivityMember> findByActivityIdIn(Collection<Integer> activityIds);

    Optional<ActivityMember> findByActivityIdAndUserId(int activityId, int userId);

    boolean existsByActivityIdAndUserId(int activityId, int userId);

    int countByActivityId(int activityId);

    void deleteByActivityId(int activityId);

}
