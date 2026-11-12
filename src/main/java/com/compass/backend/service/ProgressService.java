package com.compass.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.compass.backend.model.Task;
import com.compass.backend.model.TaskRepository;
import com.compass.backend.service.SharingService.MemberView;

/**
 * How far an activity has got, and how its tasks are shared out between the members. Computed on every
 * request from the tasks, so a change made by one member shows up for the others on their next refresh.
 */
@Service
public class ProgressService {

    /** The tasks one member took, and how many of those are done. */
    public record MemberProgress(int userId, String name, int tasksAssigned, int tasksDone) {
    }

    /** Overall progress: done tasks out of all tasks, plus the tasks nobody took yet. */
    public record ActivityProgress(int tasksTotal, int tasksDone, int tasksUnassigned, int percent,
            List<MemberProgress> members) {
    }

    private final TaskRepository tasks;
    private final SharingService sharing;

    public ProgressService(TaskRepository tasks, SharingService sharing) {
        this.tasks = tasks;
        this.sharing = sharing;
    }

    /**
     * @brief This method computes the progress of an activity.
     * @param userId the logged-in student.
     * @param activityId activity id.
     * @return the totals, the percentage and one line per current member.
     * @throws com.compass.backend.exception.ActivityNotFoundException when the student is not a member.
     */
    public ActivityProgress of(int userId, int activityId) {
        List<MemberView> members = sharing.members(userId, activityId); // also checks the student is a member
        List<Task> activityTasks = tasks.findByActivityIdOrderByIdAsc(activityId);

        int done = (int) activityTasks.stream().filter(Task::isDone).count();
        int unassigned = (int) activityTasks.stream().filter(task -> task.getAssigneeId() == null).count();
        int percent = activityTasks.isEmpty() ? 0 : Math.round(100f * done / activityTasks.size());

        List<MemberProgress> perMember = members.stream().map(member -> {
            List<Task> theirs = activityTasks.stream()
                .filter(task -> Integer.valueOf(member.userId()).equals(task.getAssigneeId())).toList();
            return new MemberProgress(member.userId(), member.name(), theirs.size(),
                (int) theirs.stream().filter(Task::isDone).count());
        }).toList();

        return new ActivityProgress(activityTasks.size(), done, unassigned, percent, perMember);
    }

}
