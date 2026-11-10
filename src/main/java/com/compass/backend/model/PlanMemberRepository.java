package com.compass.backend.model;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for plan memberships. */
public interface PlanMemberRepository extends JpaRepository<PlanMember, Integer> {

    /** The plans a student belongs to, in the order they joined them. */
    List<PlanMember> findByUserIdOrderByIdAsc(int userId);

    /** The members of a plan, owner first (the owner joins when the plan is created). */
    List<PlanMember> findByPlanIdOrderByIdAsc(int planId);

    Optional<PlanMember> findByPlanIdAndUserId(int planId, int userId);

    boolean existsByPlanIdAndUserId(int planId, int userId);

    int countByPlanId(int planId);

    void deleteByPlanId(int planId);

}
