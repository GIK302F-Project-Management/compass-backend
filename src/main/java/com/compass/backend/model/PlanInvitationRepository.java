package com.compass.backend.model;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for invitation links. */
public interface PlanInvitationRepository extends JpaRepository<PlanInvitation, Integer> {

    Optional<PlanInvitation> findByToken(String token);

    /** The invitations of a plan, newest first (including revoked and expired ones). */
    List<PlanInvitation> findByPlanIdOrderByIdDesc(int planId);

    Optional<PlanInvitation> findByIdAndPlanId(int id, int planId);

    void deleteByPlanId(int planId);

}
