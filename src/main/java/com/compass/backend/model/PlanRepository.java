package com.compass.backend.model;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for plans. Who may see a plan is decided through PlanMemberRepository. */
public interface PlanRepository extends JpaRepository<Plan, Integer> {
}
