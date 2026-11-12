package com.compass.backend.service;

import org.springframework.stereotype.Component;

import com.compass.backend.exception.ActivityNotFoundException;
import com.compass.backend.exception.NotActivityOwnerException;
import com.compass.backend.model.Activity;
import com.compass.backend.model.ActivityMemberRepository;
import com.compass.backend.model.ActivityRepository;

/**
 * The one place that decides who may touch an activity. Every service asks it before reading or changing
 * an activity, its tasks, members or invitations, so nobody sees the activities of other students.
 */
@Component
public class ActivityAccess {

    private final ActivityRepository activities;
    private final ActivityMemberRepository members;

    public ActivityAccess(ActivityRepository activities, ActivityMemberRepository members) {
        this.activities = activities;
        this.members = members;
    }

    /**
     * @brief This method loads an activity for one of its members.
     * @param userId the logged-in student.
     * @param activityId activity id.
     * @return the activity.
     * @throws ActivityNotFoundException when it does not exist or the student is not a member (the same answer).
     */
    public Activity requireMember(int userId, int activityId) {
        Activity activity = activities.findById(activityId).orElseThrow(ActivityNotFoundException::new);
        if (!members.existsByActivityIdAndUserId(activityId, userId)) {
            throw new ActivityNotFoundException();
        }
        return activity;
    }

    /**
     * @brief This method loads an activity for its owner.
     * @param userId the logged-in student.
     * @param activityId activity id.
     * @return the activity.
     * @throws ActivityNotFoundException when it does not exist or the student is not a member.
     * @throws NotActivityOwnerException when the student is a member but not the owner.
     */
    public Activity requireOwner(int userId, int activityId) {
        Activity activity = requireMember(userId, activityId);
        if (activity.getOwnerId() != userId) {
            throw new NotActivityOwnerException();
        }
        return activity;
    }

}
