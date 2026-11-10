package com.compass.backend.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.compass.backend.exception.ActivityNotFoundException;
import com.compass.backend.exception.PlanNotFoundException;
import com.compass.backend.exception.TaskNotFoundException;
import com.compass.backend.model.Activity;
import com.compass.backend.model.ActivityRepository;
import com.compass.backend.model.Task;
import com.compass.backend.model.TaskRepository;

/**
 * Business rules for the tasks of an activity. Every call carries the id of the logged-in student,
 * who must be a member of the plan the task belongs to.
 */
@Service
public class TaskService {

    private final TaskRepository tasks;
    private final ActivityRepository activities;
    private final PlanAccess access;

    public TaskService(TaskRepository tasks, ActivityRepository activities, PlanAccess access) {
        this.tasks = tasks;
        this.activities = activities;
        this.access = access;
    }

    /**
     * @brief This method lists the tasks of an activity, oldest first.
     * @throws ActivityNotFoundException when the activity does not exist or the student is not in its plan.
     */
    public List<Task> listByActivity(int userId, int activityId) {
        findActivity(userId, activityId);
        return tasks.findByActivityIdOrderByIdAsc(activityId);
    }

    /**
     * @brief This method lists every task of a plan, oldest first (one request instead of one per activity).
     * @throws PlanNotFoundException when the student is not a member of the plan.
     */
    public List<Task> listByPlan(int userId, int planId) {
        access.requireMember(userId, planId);
        return tasks.findByPlanIdOrderByIdAsc(planId);
    }

    /**
     * @brief This method adds a task to an activity. A task added to a completed activity reopens it,
     *        because the activity now has something left to do.
     * @param dueDate the task's own deadline, or null.
     * @param estimatedMinutes how long it takes, or null when nobody estimated it.
     * @return the saved task.
     * @throws ActivityNotFoundException when the activity does not exist or the student is not in its plan.
     */
    @Transactional
    public Task create(int userId, int activityId, String title, String details, LocalDate dueDate,
            Integer estimatedMinutes) {
        Activity activity = findActivity(userId, activityId);
        if (activity.isDone()) {
            activity.setDone(false, userId);
            activities.save(activity);
        }
        return tasks.save(new Task(activityId, activity.getPlanId(), title, details, dueDate, estimatedMinutes, userId));
    }

    /**
     * @brief This method changes some fields of a task and leaves the others as they are, so two students
     *        editing different fields do not overwrite each other.
     * @throws TaskNotFoundException when the task does not exist or the student is not in its plan.
     */
    @Transactional
    public Task update(int userId, int id, TaskChanges changes) {
        Task task = find(userId, id);
        changes.applyTo(task, userId);
        if (!task.isDone()) {
            // An open task means the activity is not finished.
            activities.findById(task.getActivityId()).filter(Activity::isDone).ifPresent(activity -> {
                activity.setDone(false, userId);
                activities.save(activity);
            });
        }
        return tasks.save(task);
    }

    /**
     * @brief This method deletes a task.
     * @throws TaskNotFoundException when the task does not exist or the student is not in its plan.
     */
    public void delete(int userId, int id) {
        tasks.delete(find(userId, id));
    }

    private Task find(int userId, int id) {
        Task task = tasks.findById(id).orElseThrow(TaskNotFoundException::new);
        try {
            access.requireMember(userId, task.getPlanId());
        } catch (PlanNotFoundException ex) {
            throw new TaskNotFoundException();
        }
        return task;
    }

    private Activity findActivity(int userId, int activityId) {
        Activity activity = activities.findById(activityId).orElseThrow(ActivityNotFoundException::new);
        try {
            access.requireMember(userId, activity.getPlanId());
        } catch (PlanNotFoundException ex) {
            throw new ActivityNotFoundException();
        }
        return activity;
    }

}
