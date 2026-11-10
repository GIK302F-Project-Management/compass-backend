package com.compass.backend.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.compass.backend.exception.ActivityNotFoundException;
import com.compass.backend.exception.PlanNotFoundException;
import com.compass.backend.model.Activity;
import com.compass.backend.model.ActivityRepository;
import com.compass.backend.model.Task;
import com.compass.backend.model.TaskRepository;

/**
 * Business rules for the activities of a plan. Every call carries the id of the logged-in student,
 * who must be a member of the plan the activity belongs to.
 */
@Service
public class ActivityService {

    private final ActivityRepository activities;
    private final TaskRepository tasks;
    private final PlanAccess access;

    public ActivityService(ActivityRepository activities, TaskRepository tasks, PlanAccess access) {
        this.activities = activities;
        this.tasks = tasks;
        this.access = access;
    }

    /**
     * @brief This method lists the activities of a plan, oldest first.
     * @param userId the logged-in student.
     * @param planId plan id.
     * @return the plan's activities.
     * @throws PlanNotFoundException when the student is not a member of the plan.
     */
    public List<Activity> list(int userId, int planId) {
        access.requireMember(userId, planId);
        return activities.findByPlanIdOrderByIdAsc(planId);
    }

    /**
     * @brief This method adds an activity to a plan.
     * @param userId the logged-in student.
     * @param planId plan id.
     * @param title activity title (already trimmed and validated).
     * @param subject subject or area (already trimmed and validated).
     * @param dueDate due date.
     * @return the saved activity.
     * @throws PlanNotFoundException when the student is not a member of the plan.
     */
    public Activity create(int userId, int planId, String title, String subject, LocalDate dueDate) {
        access.requireMember(userId, planId);
        return activities.save(new Activity(planId, title, subject, dueDate, userId));
    }

    /**
     * @brief This method shows one activity.
     * @param userId the logged-in student.
     * @param id activity id.
     * @return the activity.
     * @throws ActivityNotFoundException when it does not exist or the student is not a member of its plan.
     */
    public Activity get(int userId, int id) {
        return find(userId, id);
    }

    /**
     * @brief This method replaces the title, subject and due date of an activity.
     * @param userId the logged-in student.
     * @param id activity id.
     * @return the saved activity.
     * @throws ActivityNotFoundException when it does not exist or the student is not a member of its plan.
     */
    public Activity update(int userId, int id, String title, String subject, LocalDate dueDate) {
        Activity activity = find(userId, id);
        activity.setTitle(title);
        activity.setSubject(subject);
        activity.setDueDate(dueDate);
        return activities.save(activity);
    }

    /**
     * @brief This method completes or reopens an activity. Completing it also completes its open tasks,
     *        credited to the same student; reopening it leaves the tasks as they are.
     * @param userId the logged-in student.
     * @param id activity id.
     * @param done true to complete, false to reopen.
     * @return the saved activity.
     * @throws ActivityNotFoundException when it does not exist or the student is not a member of its plan.
     */
    @Transactional
    public Activity setDone(int userId, int id, boolean done) {
        Activity activity = find(userId, id);
        if (done == activity.isDone()) {
            return activity;
        }
        activity.setDone(done, userId);
        if (done) {
            for (Task task : tasks.findByActivityIdOrderByIdAsc(id)) {
                if (!task.isDone()) {
                    task.setDone(true, userId);
                    tasks.save(task);
                }
            }
        }
        return activities.save(activity);
    }

    /**
     * @brief This method deletes an activity with its tasks.
     * @param userId the logged-in student.
     * @param id activity id.
     * @throws ActivityNotFoundException when it does not exist or the student is not a member of its plan.
     */
    @Transactional
    public void delete(int userId, int id) {
        Activity activity = find(userId, id);
        tasks.deleteByActivityId(id);
        activities.delete(activity);
    }

    private Activity find(int userId, int id) {
        Activity activity = activities.findById(id).orElseThrow(ActivityNotFoundException::new);
        try {
            access.requireMember(userId, activity.getPlanId());
        } catch (PlanNotFoundException ex) {
            throw new ActivityNotFoundException();
        }
        return activity;
    }

}
