package com.compass.backend.service;

import org.springframework.stereotype.Component;

import com.compass.backend.exception.NotPlanOwnerException;
import com.compass.backend.exception.PlanNotFoundException;
import com.compass.backend.model.Plan;
import com.compass.backend.model.PlanMemberRepository;
import com.compass.backend.model.PlanRepository;

/**
 * The one place that decides who may touch a plan. Every service asks it before reading or changing
 * anything that belongs to a plan, so nobody sees the plans of other students.
 */
@Component
public class PlanAccess {

    private final PlanRepository plans;
    private final PlanMemberRepository members;

    public PlanAccess(PlanRepository plans, PlanMemberRepository members) {
        this.plans = plans;
        this.members = members;
    }

    /**
     * @brief This method loads a plan for one of its members.
     * @param userId the logged-in student.
     * @param planId plan id.
     * @return the plan.
     * @throws PlanNotFoundException when the plan does not exist or the student is not a member.
     */
    public Plan requireMember(int userId, int planId) {
        Plan plan = plans.findById(planId).orElseThrow(PlanNotFoundException::new);
        if (!members.existsByPlanIdAndUserId(planId, userId)) {
            throw new PlanNotFoundException();
        }
        return plan;
    }

    /**
     * @brief This method loads a plan for its owner.
     * @param userId the logged-in student.
     * @param planId plan id.
     * @return the plan.
     * @throws PlanNotFoundException when the plan does not exist or the student is not a member.
     * @throws NotPlanOwnerException when the student is a member but not the owner.
     */
    public Plan requireOwner(int userId, int planId) {
        Plan plan = requireMember(userId, planId);
        if (plan.getOwnerId() != userId) {
            throw new NotPlanOwnerException();
        }
        return plan;
    }

}
