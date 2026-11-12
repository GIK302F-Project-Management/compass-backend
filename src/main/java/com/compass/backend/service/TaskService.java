package com.compass.backend.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.compass.backend.exception.ActivityNotFoundException;
import com.compass.backend.exception.InvalidTaskChangesException;
import com.compass.backend.exception.TaskNotFoundException;
import com.compass.backend.model.Activity;
import com.compass.backend.model.ActivityMemberRepository;
import com.compass.backend.model.ActivityRepository;
import com.compass.backend.model.Task;
import com.compass.backend.model.TaskRepository;

/**
 * Business rules for the tasks of an activity. Every member of the activity sees its tasks and can change
 * them; each task has an assignee, the member whose route it goes into. Every call carries the id of the
 * logged-in student.
 */
@Service
public class TaskService {

    private final TaskRepository tasks;
    private final ActivityRepository activities;
    private final ActivityMemberRepository members;
    private final ActivityAccess access;

    public TaskService(TaskRepository tasks, ActivityRepository activities, ActivityMemberRepository members,
            ActivityAccess access) {
        this.tasks = tasks;
        this.activities = activities;
        this.members = members;
        this.access = access;
    }

    /**
     * @brief This method lists the tasks of an activity, oldest first.
     * @throws ActivityNotFoundException when the activity does not exist or the student is not a member.
     */
    public List<Task> listByActivity(int userId, int activityId) {
        access.requireMember(userId, activityId);
        return tasks.findByActivityIdOrderByIdAsc(activityId);
    }

    /**
     * @brief This method lists every task of every activity in the student's atlas, oldest first, whoever they
     *        are assigned to (one request instead of one per activity; the route planner keeps the student's own).
     */
    public List<Task> listForMember(int userId) {
        return tasks.findForMember(userId);
    }

    /**
     * @brief This method adds a task to an activity, assigned to the student who adds it. A task added to a
     *        completed activity reopens it, because the activity now has something left to do.
     * @param dueDate the task's own deadline, or null.
     * @param estimatedMinutes how long it takes, or null when nobody estimated it.
     * @return the saved task.
     * @throws ActivityNotFoundException when the activity does not exist or the student is not a member.
     */
    @Transactional
    public Task create(int userId, int activityId, String title, String details, LocalDate dueDate,
            Integer estimatedMinutes) {
        Activity activity = access.requireMember(userId, activityId);
        if (activity.isDone()) {
            activity.setDone(false, userId);
            activities.save(activity);
        }
        return tasks.save(new Task(activityId, title, details, dueDate, estimatedMinutes, userId));
    }

    /**
     * @brief This method changes some fields of a task and leaves the others as they are, so two students
     *        editing different fields do not overwrite each other.
     * @throws TaskNotFoundException when the task does not exist or the student is not a member of its activity.
     * @throws InvalidTaskChangesException when the new assignee is not a member of the activity.
     */
    @Transactional
    public Task update(int userId, int id, TaskChanges changes) {
        Task task = find(userId, id);
        Integer assignee = changes.newAssignee();
        if (assignee != null && !members.existsByActivityIdAndUserId(task.getActivityId(), assignee)) {
            throw new InvalidTaskChangesException(Map.of("assigneeId", "That student is not in this activity."));
        }
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
     * @throws TaskNotFoundException when the task does not exist or the student is not a member of its activity.
     */
    public void delete(int userId, int id) {
        tasks.delete(find(userId, id));
    }

    private Task find(int userId, int id) {
        Task task = tasks.findById(id).orElseThrow(TaskNotFoundException::new);
        try {
            access.requireMember(userId, task.getActivityId());
        } catch (ActivityNotFoundException ex) {
            throw new TaskNotFoundException();
        }
        return task;
    }

}
