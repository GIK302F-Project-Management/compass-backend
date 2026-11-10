package com.compass.backend.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.compass.backend.model.Activity;
import com.compass.backend.model.ActivityRepository;
import com.compass.backend.model.PlanMember;
import com.compass.backend.model.PlanMemberRepository;
import com.compass.backend.model.Task;
import com.compass.backend.model.TaskRepository;
import com.compass.backend.model.User;
import com.compass.backend.model.UserRepository;

/**
 * The overall progress of a plan, and what each member has completed. Computed on every request from
 * the activities and tasks, so a change made by one member shows up for all the others on their next refresh.
 */
@Service
public class ProgressService {

    /** What one member completed. */
    public record MemberProgress(int userId, String name, int tasksDone, int activitiesDone) {
    }

    /**
     * Overall progress. The percentage counts every task, plus every activity that has no tasks,
     * as one step each, so a big activity weighs more than a small one.
     */
    public record PlanProgress(int activitiesTotal, int activitiesDone, int tasksTotal, int tasksDone, int percent,
            List<MemberProgress> members) {
    }

    private final ActivityRepository activities;
    private final TaskRepository tasks;
    private final PlanMemberRepository members;
    private final UserRepository users;
    private final PlanAccess access;

    public ProgressService(ActivityRepository activities, TaskRepository tasks, PlanMemberRepository members,
            UserRepository users, PlanAccess access) {
        this.activities = activities;
        this.tasks = tasks;
        this.members = members;
        this.users = users;
        this.access = access;
    }

    /**
     * @brief This method computes the progress of a plan.
     * @param userId the logged-in student.
     * @param planId plan id.
     * @return the totals, the percentage and one line per current member.
     * @throws com.compass.backend.exception.PlanNotFoundException when the student is not a member.
     */
    public PlanProgress of(int userId, int planId) {
        access.requireMember(userId, planId);
        List<Activity> planActivities = activities.findByPlanIdOrderByIdAsc(planId);
        List<Task> planTasks = tasks.findByPlanIdOrderByIdAsc(planId);

        Set<Integer> activitiesWithTasks = new HashSet<>();
        planTasks.forEach(task -> activitiesWithTasks.add(task.getActivityId()));

        int activitiesDone = (int) planActivities.stream().filter(Activity::isDone).count();
        int tasksDone = (int) planTasks.stream().filter(Task::isDone).count();
        int steps = planTasks.size();
        int stepsDone = tasksDone;
        for (Activity activity : planActivities) {
            if (!activitiesWithTasks.contains(activity.getId())) {
                steps++;
                if (activity.isDone()) {
                    stepsDone++;
                }
            }
        }
        int percent = steps == 0 ? 0 : Math.round(100f * stepsDone / steps);

        List<PlanMember> memberships = members.findByPlanIdOrderByIdAsc(planId);
        List<MemberProgress> perMember = memberships.stream().map(membership -> {
            int memberId = membership.getUserId();
            String name = users.findById(memberId).map(User::getName).orElse("");
            int memberTasks = (int) planTasks.stream()
                .filter(task -> task.isDone() && Integer.valueOf(memberId).equals(task.getDoneBy())).count();
            int memberActivities = (int) planActivities.stream()
                .filter(activity -> activity.isDone() && Integer.valueOf(memberId).equals(activity.getDoneBy())).count();
            return new MemberProgress(memberId, name, memberTasks, memberActivities);
        }).toList();

        return new PlanProgress(planActivities.size(), activitiesDone, planTasks.size(), tasksDone, percent, perMember);
    }

}
