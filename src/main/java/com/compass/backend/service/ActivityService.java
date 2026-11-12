package com.compass.backend.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.compass.backend.exception.ActivityNotFoundException;
import com.compass.backend.exception.NotActivityOwnerException;
import com.compass.backend.exception.UserNotFoundException;
import com.compass.backend.model.Activity;
import com.compass.backend.model.ActivityInvitationRepository;
import com.compass.backend.model.ActivityMember;
import com.compass.backend.model.ActivityMemberRepository;
import com.compass.backend.model.ActivityRepository;
import com.compass.backend.model.Task;
import com.compass.backend.model.TaskRepository;
import com.compass.backend.model.UserRepository;

/**
 * Business rules for the activities in a student's atlas: their own and the ones shared with them. Every
 * member can edit and complete an activity; only the owner can delete it. Every call carries the id of
 * the logged-in student.
 */
@Service
public class ActivityService {

    /** An activity and how many students are in it. */
    public record ActivityView(Activity activity, int memberCount) {
    }

    private final ActivityRepository activities;
    private final ActivityMemberRepository members;
    private final ActivityInvitationRepository invitations;
    private final TaskRepository tasks;
    private final UserRepository users;
    private final ActivityAccess access;

    public ActivityService(ActivityRepository activities, ActivityMemberRepository members,
            ActivityInvitationRepository invitations, TaskRepository tasks, UserRepository users,
            ActivityAccess access) {
        this.activities = activities;
        this.members = members;
        this.invitations = invitations;
        this.tasks = tasks;
        this.users = users;
        this.access = access;
    }

    /**
     * @brief This method lists every activity in the student's atlas: their own and the shared ones, oldest first.
     * @param userId the logged-in student.
     * @return the activities, each with its member count.
     */
    public List<ActivityView> list(int userId) {
        List<Activity> found = activities.findForMember(userId);
        Map<Integer, Long> counts = members.findByActivityIdIn(found.stream().map(Activity::getId).toList())
            .stream().collect(Collectors.groupingBy(ActivityMember::getActivityId, Collectors.counting()));
        return found.stream()
            .map(activity -> new ActivityView(activity, counts.getOrDefault(activity.getId(), 1L).intValue()))
            .toList();
    }

    /**
     * @brief This method adds an activity to the student's atlas, with the student as its owner.
     * @param userId the logged-in student.
     * @param title activity title (already trimmed and validated).
     * @param subject subject or area (already trimmed and validated).
     * @param dueDate due date.
     * @return the saved activity.
     * @throws UserNotFoundException when the account no longer exists.
     */
    @Transactional
    public ActivityView create(int userId, String title, String subject, LocalDate dueDate) {
        if (!users.existsById(userId)) {
            throw new UserNotFoundException();
        }
        Activity activity = activities.save(new Activity(userId, title, subject, dueDate));
        members.save(new ActivityMember(activity.getId(), userId));
        return new ActivityView(activity, 1);
    }

    /**
     * @brief This method shows one activity.
     * @throws ActivityNotFoundException when it does not exist or the student is not a member.
     */
    public ActivityView get(int userId, int id) {
        return view(access.requireMember(userId, id));
    }

    /**
     * @brief This method replaces the title, subject and due date of an activity. Any member can do it.
     * @throws ActivityNotFoundException when it does not exist or the student is not a member.
     */
    public ActivityView update(int userId, int id, String title, String subject, LocalDate dueDate) {
        Activity activity = access.requireMember(userId, id);
        activity.setTitle(title);
        activity.setSubject(subject);
        activity.setDueDate(dueDate);
        return view(activities.save(activity));
    }

    /**
     * @brief This method completes or reopens an activity. Completing it also completes its open tasks,
     *        credited to the same student; reopening it leaves the tasks as they are.
     * @param done true to complete, false to reopen.
     * @throws ActivityNotFoundException when it does not exist or the student is not a member.
     */
    @Transactional
    public ActivityView setDone(int userId, int id, boolean done) {
        Activity activity = access.requireMember(userId, id);
        if (done == activity.isDone()) {
            return view(activity);
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
        return view(activities.save(activity));
    }

    /**
     * @brief This method deletes an activity with its tasks, members and invitations, for every member.
     * @throws ActivityNotFoundException when it does not exist or the student is not a member.
     * @throws NotActivityOwnerException when the student is a member but not the owner.
     */
    @Transactional
    public void delete(int userId, int id) {
        Activity activity = access.requireOwner(userId, id);
        tasks.deleteByActivityId(id);
        invitations.deleteByActivityId(id);
        members.deleteByActivityId(id);
        activities.delete(activity);
    }

    private ActivityView view(Activity activity) {
        return new ActivityView(activity, members.countByActivityId(activity.getId()));
    }

}
