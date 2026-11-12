package com.compass.backend.model;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for invitation links. */
public interface ActivityInvitationRepository extends JpaRepository<ActivityInvitation, Integer> {

    Optional<ActivityInvitation> findByToken(String token);

    /** The invitations of an activity, newest first (including revoked and expired ones). */
    List<ActivityInvitation> findByActivityIdOrderByIdDesc(int activityId);

    Optional<ActivityInvitation> findByIdAndActivityId(int id, int activityId);

    void deleteByActivityId(int activityId);

}
