package com.compass.backend.model;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for learning goals. Every method takes the student's id, so a query can
 * never return a goal that belongs to someone else.
 */
public interface GoalRepository extends JpaRepository<Goal, Integer> {

    /**
     * @brief This method lists the goals of one student, oldest first.
     * @param userId the student's id.
     * @return the student's goals (empty when there are none).
     */
    List<Goal> findByUserIdOrderByIdAsc(int userId);

    /**
     * @brief This method finds one goal, but only when it belongs to the given student.
     * @param id goal id.
     * @param userId the student's id.
     * @return the goal, or empty when it does not exist or belongs to another student.
     */
    Optional<Goal> findByIdAndUserId(int id, int userId);

}
