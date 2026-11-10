package com.compass.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.compass.backend.exception.GoalNotFoundException;
import com.compass.backend.exception.UserNotFoundException;
import com.compass.backend.model.Goal;
import com.compass.backend.model.GoalRepository;
import com.compass.backend.model.UserRepository;

/**
 * Business rules for learning goals. Every call carries the id of the logged-in student and only
 * touches that student's goals.
 */
@Service
public class GoalService {

    private final GoalRepository goals;
    private final UserRepository users;

    public GoalService(GoalRepository goals, UserRepository users) {
        this.goals = goals;
        this.users = users;
    }

    /**
     * @brief This method lists the student's goals, oldest first.
     * @param userId the logged-in student.
     * @return the student's goals.
     */
    public List<Goal> list(int userId) {
        return goals.findByUserIdOrderByIdAsc(userId);
    }

    /**
     * @brief This method creates a goal for the student.
     * @param userId the logged-in student.
     * @param title goal title (already trimmed and validated).
     * @param description goal description (may be empty).
     * @return the saved goal.
     * @throws UserNotFoundException when the account no longer exists.
     */
    public Goal create(int userId, String title, String description) {
        if (!users.existsById(userId)) {
            throw new UserNotFoundException();
        }
        return goals.save(new Goal(userId, title, description));
    }

    /**
     * @brief This method changes the title and description of one of the student's goals.
     * @param userId the logged-in student.
     * @param id goal id.
     * @param title new title (already trimmed and validated).
     * @param description new description (may be empty).
     * @return the saved goal.
     * @throws GoalNotFoundException when the goal does not exist or belongs to another student.
     */
    public Goal update(int userId, int id, String title, String description) {
        Goal goal = find(userId, id);
        goal.setTitle(title);
        goal.setDescription(description);
        return goals.save(goal);
    }

    /**
     * @brief This method deletes one of the student's goals.
     * @param userId the logged-in student.
     * @param id goal id.
     * @throws GoalNotFoundException when the goal does not exist or belongs to another student.
     */
    public void delete(int userId, int id) {
        goals.delete(find(userId, id));
    }

    private Goal find(int userId, int id) {
        return goals.findByIdAndUserId(id, userId).orElseThrow(GoalNotFoundException::new);
    }

}
