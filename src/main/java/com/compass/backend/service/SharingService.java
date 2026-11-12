package com.compass.backend.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.compass.backend.exception.ActivityFullException;
import com.compass.backend.exception.InvitationNotFoundException;
import com.compass.backend.exception.MemberNotFoundException;
import com.compass.backend.exception.OwnerCannotLeaveException;
import com.compass.backend.exception.UserNotFoundException;
import com.compass.backend.model.Activity;
import com.compass.backend.model.ActivityInvitation;
import com.compass.backend.model.ActivityInvitationRepository;
import com.compass.backend.model.ActivityMember;
import com.compass.backend.model.ActivityMemberRepository;
import com.compass.backend.model.ActivityRepository;
import com.compass.backend.model.TaskRepository;
import com.compass.backend.model.User;
import com.compass.backend.model.UserRepository;
import com.compass.backend.service.ActivityService.ActivityView;

/**
 * Sharing an activity with other students: who is in it, and how others join it through an invitation
 * link. Every call carries the id of the logged-in student.
 */
@Service
public class SharingService {

    public static final Duration INVITATION_LIFETIME = Duration.ofDays(7);

    private static final SecureRandom RANDOM = new SecureRandom();

    /** A student in an activity. Only the name is shared with the other members, never the email. */
    public record MemberView(int userId, String name, boolean owner, Instant joinedAt) {
    }

    /** What someone sees before accepting an invitation. */
    public record InvitationPreview(String activityTitle, String subject, LocalDate dueDate,
            String ownerName, int memberCount, boolean alreadyMember) {
    }

    private final ActivityRepository activities;
    private final ActivityMemberRepository members;
    private final ActivityInvitationRepository invitations;
    private final TaskRepository tasks;
    private final UserRepository users;
    private final ActivityAccess access;

    public SharingService(ActivityRepository activities, ActivityMemberRepository members,
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
     * @brief This method lists the members of an activity, the owner first.
     * @throws com.compass.backend.exception.ActivityNotFoundException when the student is not a member.
     */
    public List<MemberView> members(int userId, int activityId) {
        Activity activity = access.requireMember(userId, activityId);
        List<ActivityMember> memberships = members.findByActivityIdOrderByIdAsc(activityId);
        Map<Integer, User> byId = users.findAllById(memberships.stream().map(ActivityMember::getUserId).toList())
            .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return memberships.stream()
            .filter(membership -> byId.containsKey(membership.getUserId()))
            .map(membership -> new MemberView(membership.getUserId(), byId.get(membership.getUserId()).getName(),
                membership.getUserId() == activity.getOwnerId(), membership.getJoinedAt()))
            .toList();
    }

    /**
     * @brief This method takes a student out of an activity: a member leaving it, or the owner removing a
     *        member. Their tasks stay in the activity without an assignee, so the others can take them.
     * @param memberId the student to take out (the same as userId to leave).
     * @throws OwnerCannotLeaveException when the owner tries to leave their own activity.
     * @throws com.compass.backend.exception.NotActivityOwnerException when a member removes someone else.
     * @throws MemberNotFoundException when the other student is not in the activity.
     */
    @Transactional
    public void removeMember(int userId, int activityId, int memberId) {
        Activity activity = access.requireMember(userId, activityId);
        if (memberId == userId) {
            if (activity.getOwnerId() == userId) {
                throw new OwnerCannotLeaveException();
            }
        } else {
            access.requireOwner(userId, activityId);
        }
        ActivityMember membership = members.findByActivityIdAndUserId(activityId, memberId)
            .orElseThrow(MemberNotFoundException::new);
        members.delete(membership);
        tasks.unassign(activityId, memberId);
    }

    /**
     * @brief This method creates an invitation link that works for a week.
     * @throws com.compass.backend.exception.NotActivityOwnerException when the student is not the owner.
     */
    @Transactional
    public ActivityInvitation invite(int userId, int activityId) {
        access.requireOwner(userId, activityId);
        return invitations.save(
            new ActivityInvitation(activityId, newToken(), userId, Instant.now().plus(INVITATION_LIFETIME)));
    }

    /**
     * @brief This method lists the invitations that still work, newest first.
     * @throws com.compass.backend.exception.NotActivityOwnerException when the student is not the owner.
     */
    public List<ActivityInvitation> activeInvitations(int userId, int activityId) {
        access.requireOwner(userId, activityId);
        Instant now = Instant.now();
        return invitations.findByActivityIdOrderByIdDesc(activityId).stream()
            .filter(invitation -> invitation.isActive(now)).toList();
    }

    /**
     * @brief This method revokes an invitation, so its link stops working.
     * @throws InvitationNotFoundException when the activity has no such invitation.
     */
    @Transactional
    public void revokeInvitation(int userId, int activityId, int invitationId) {
        access.requireOwner(userId, activityId);
        ActivityInvitation invitation = invitations.findByIdAndActivityId(invitationId, activityId)
            .orElseThrow(InvitationNotFoundException::new);
        invitation.revoke();
        invitations.save(invitation);
    }

    /**
     * @brief This method shows what an invitation is for, without joining.
     * @throws InvitationNotFoundException when the token is unknown, expired or revoked.
     */
    public InvitationPreview preview(int userId, String token) {
        Activity activity = activityOf(activeInvitation(token));
        String ownerName = users.findById(activity.getOwnerId()).map(User::getName).orElse("");
        return new InvitationPreview(activity.getTitle(), activity.getSubject(), activity.getDueDate(), ownerName,
            members.countByActivityId(activity.getId()),
            members.existsByActivityIdAndUserId(activity.getId(), userId));
    }

    /**
     * @brief This method makes the student a member of the activity the invitation is for, so it shows up in
     *        their atlas. Accepting twice is fine: the student stays a member once.
     * @throws InvitationNotFoundException when the token is unknown, expired or revoked.
     * @throws ActivityFullException when the activity has reached its member limit.
     */
    @Transactional
    public ActivityView accept(int userId, String token) {
        if (!users.existsById(userId)) {
            throw new UserNotFoundException();
        }
        Activity activity = activityOf(activeInvitation(token));
        if (!members.existsByActivityIdAndUserId(activity.getId(), userId)) {
            if (members.countByActivityId(activity.getId()) >= Activity.MAX_MEMBERS) {
                throw new ActivityFullException();
            }
            members.save(new ActivityMember(activity.getId(), userId));
        }
        return new ActivityView(activity, members.countByActivityId(activity.getId()));
    }

    private ActivityInvitation activeInvitation(String token) {
        return invitations.findByToken(token).filter(invitation -> invitation.isActive(Instant.now()))
            .orElseThrow(InvitationNotFoundException::new);
    }

    private Activity activityOf(ActivityInvitation invitation) {
        return activities.findById(invitation.getActivityId()).orElseThrow(InvitationNotFoundException::new);
    }

    /** 32 random bytes as URL-safe text: 256 bits, so it cannot be guessed. */
    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

}
