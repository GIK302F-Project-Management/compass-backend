package com.compass.backend.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.compass.backend.exception.InvitationNotFoundException;
import com.compass.backend.exception.MemberNotFoundException;
import com.compass.backend.exception.OwnerCannotLeaveException;
import com.compass.backend.exception.PlanFullException;
import com.compass.backend.exception.UserNotFoundException;
import com.compass.backend.model.ActivityRepository;
import com.compass.backend.model.Plan;
import com.compass.backend.model.PlanInvitation;
import com.compass.backend.model.PlanInvitationRepository;
import com.compass.backend.model.PlanMember;
import com.compass.backend.model.PlanMemberRepository;
import com.compass.backend.model.PlanRepository;
import com.compass.backend.model.TaskRepository;
import com.compass.backend.model.User;
import com.compass.backend.model.UserRepository;

/**
 * Business rules for study plans shared between students: who is in a plan, and how others join it
 * through an invitation link. Every call carries the id of the logged-in student.
 */
@Service
public class PlanService {

    public static final String PERSONAL_PLAN_NAME = "My plan";
    public static final Duration INVITATION_LIFETIME = Duration.ofDays(7);

    private static final SecureRandom RANDOM = new SecureRandom();

    /** A plan and how many students are in it. */
    public record PlanSummary(Plan plan, int memberCount) {
    }

    /** A student in a plan. Only the name is shared with the other members, never the email. */
    public record MemberView(int userId, String name, boolean owner, Instant joinedAt) {
    }

    public record PlanDetails(Plan plan, List<MemberView> members) {
    }

    /** What someone sees before accepting an invitation. */
    public record InvitationPreview(String planName, String ownerName, int memberCount, boolean alreadyMember) {
    }

    private final PlanRepository plans;
    private final PlanMemberRepository members;
    private final PlanInvitationRepository invitations;
    private final ActivityRepository activities;
    private final TaskRepository tasks;
    private final UserRepository users;
    private final PlanAccess access;

    public PlanService(PlanRepository plans, PlanMemberRepository members, PlanInvitationRepository invitations,
            ActivityRepository activities, TaskRepository tasks, UserRepository users, PlanAccess access) {
        this.plans = plans;
        this.members = members;
        this.invitations = invitations;
        this.activities = activities;
        this.tasks = tasks;
        this.users = users;
        this.access = access;
    }

    /**
     * @brief This method lists the plans the student is in, in the order they joined them.
     *        A student who has none gets a personal plan first, so there is always one to work in.
     * @param userId the logged-in student.
     * @return the student's plans.
     * @throws UserNotFoundException when the account no longer exists.
     */
    @Transactional
    public List<PlanSummary> list(int userId) {
        requireUser(userId);
        List<PlanMember> memberships = members.findByUserIdOrderByIdAsc(userId);
        if (memberships.isEmpty()) {
            return List.of(summary(createPlan(userId, PERSONAL_PLAN_NAME)));
        }
        Map<Integer, Plan> byId = plans.findAllById(memberships.stream().map(PlanMember::getPlanId).toList())
            .stream().collect(Collectors.toMap(Plan::getId, Function.identity()));
        return memberships.stream().map(membership -> summary(byId.get(membership.getPlanId()))).toList();
    }

    /**
     * @brief This method creates a plan owned by the student.
     * @param userId the logged-in student.
     * @param name plan name (already trimmed and validated).
     * @return the new plan.
     * @throws UserNotFoundException when the account no longer exists.
     */
    @Transactional
    public PlanSummary create(int userId, String name) {
        requireUser(userId);
        return summary(createPlan(userId, name));
    }

    /**
     * @brief This method shows one plan with its members.
     * @param userId the logged-in student.
     * @param planId plan id.
     * @return the plan and its members, the owner first.
     * @throws com.compass.backend.exception.PlanNotFoundException when the student is not a member.
     */
    public PlanDetails get(int userId, int planId) {
        Plan plan = access.requireMember(userId, planId);
        List<PlanMember> memberships = members.findByPlanIdOrderByIdAsc(planId);
        Map<Integer, User> byId = users.findAllById(memberships.stream().map(PlanMember::getUserId).toList())
            .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        List<MemberView> views = memberships.stream()
            .map(membership -> new MemberView(membership.getUserId(), byId.get(membership.getUserId()).getName(),
                membership.getUserId() == plan.getOwnerId(), membership.getJoinedAt()))
            .toList();
        return new PlanDetails(plan, views);
    }

    /**
     * @brief This method renames a plan.
     * @param userId the logged-in student, who must be the owner.
     * @param planId plan id.
     * @param name new name (already trimmed and validated).
     * @return the renamed plan.
     * @throws com.compass.backend.exception.NotPlanOwnerException when the student is not the owner.
     */
    @Transactional
    public PlanSummary rename(int userId, int planId, String name) {
        Plan plan = access.requireOwner(userId, planId);
        plan.setName(name);
        return summary(plans.save(plan));
    }

    /**
     * @brief This method deletes a plan with its members, invitations, activities and tasks.
     * @param userId the logged-in student, who must be the owner.
     * @param planId plan id.
     * @throws com.compass.backend.exception.NotPlanOwnerException when the student is not the owner.
     */
    @Transactional
    public void delete(int userId, int planId) {
        Plan plan = access.requireOwner(userId, planId);
        tasks.deleteByPlanId(planId);
        activities.deleteByPlanId(planId);
        invitations.deleteByPlanId(planId);
        members.deleteByPlanId(planId);
        plans.delete(plan);
    }

    /**
     * @brief This method takes a student out of a plan: a member leaving it, or the owner removing a member.
     *        What the student completed stays in the plan.
     * @param userId the logged-in student.
     * @param planId plan id.
     * @param memberId the student to take out (the same as userId to leave).
     * @throws OwnerCannotLeaveException when the owner tries to leave their own plan.
     * @throws com.compass.backend.exception.NotPlanOwnerException when a member removes someone else.
     * @throws MemberNotFoundException when the other student is not in the plan.
     */
    @Transactional
    public void removeMember(int userId, int planId, int memberId) {
        Plan plan = access.requireMember(userId, planId);
        if (memberId == userId) {
            if (plan.getOwnerId() == userId) {
                throw new OwnerCannotLeaveException();
            }
        } else {
            access.requireOwner(userId, planId);
        }
        PlanMember membership = members.findByPlanIdAndUserId(planId, memberId)
            .orElseThrow(MemberNotFoundException::new);
        members.delete(membership);
    }

    /**
     * @brief This method creates an invitation link that works for a week.
     * @param userId the logged-in student, who must be the owner.
     * @param planId plan id.
     * @return the invitation, with the token to put in the link.
     * @throws com.compass.backend.exception.NotPlanOwnerException when the student is not the owner.
     */
    @Transactional
    public PlanInvitation invite(int userId, int planId) {
        access.requireOwner(userId, planId);
        return invitations.save(new PlanInvitation(planId, newToken(), userId, Instant.now().plus(INVITATION_LIFETIME)));
    }

    /**
     * @brief This method lists the invitations that still work, newest first.
     * @param userId the logged-in student, who must be the owner.
     * @param planId plan id.
     * @return the active invitations.
     */
    public List<PlanInvitation> activeInvitations(int userId, int planId) {
        access.requireOwner(userId, planId);
        Instant now = Instant.now();
        return invitations.findByPlanIdOrderByIdDesc(planId).stream().filter(invitation -> invitation.isActive(now)).toList();
    }

    /**
     * @brief This method revokes an invitation, so its link stops working.
     * @param userId the logged-in student, who must be the owner.
     * @param planId plan id.
     * @param invitationId invitation id.
     * @throws InvitationNotFoundException when the plan has no such invitation.
     */
    @Transactional
    public void revokeInvitation(int userId, int planId, int invitationId) {
        access.requireOwner(userId, planId);
        PlanInvitation invitation = invitations.findByIdAndPlanId(invitationId, planId)
            .orElseThrow(InvitationNotFoundException::new);
        invitation.revoke();
        invitations.save(invitation);
    }

    /**
     * @brief This method shows what an invitation is for, without joining.
     * @param userId the logged-in student.
     * @param token the token from the link.
     * @return the plan name, its owner, how many students are in it and whether this student already is.
     * @throws InvitationNotFoundException when the token is unknown, expired or revoked.
     */
    public InvitationPreview preview(int userId, String token) {
        PlanInvitation invitation = activeInvitation(token);
        Plan plan = plans.findById(invitation.getPlanId()).orElseThrow(InvitationNotFoundException::new);
        String ownerName = users.findById(plan.getOwnerId()).map(User::getName).orElse("");
        return new InvitationPreview(plan.getName(), ownerName, members.countByPlanId(plan.getId()),
            members.existsByPlanIdAndUserId(plan.getId(), userId));
    }

    /**
     * @brief This method makes the student a member of the plan the invitation is for.
     *        Accepting twice is fine: the student stays a member once.
     * @param userId the logged-in student.
     * @param token the token from the link.
     * @return the plan the student joined.
     * @throws InvitationNotFoundException when the token is unknown, expired or revoked.
     * @throws PlanFullException when the plan has reached its member limit.
     */
    @Transactional
    public PlanSummary accept(int userId, String token) {
        requireUser(userId);
        PlanInvitation invitation = activeInvitation(token);
        Plan plan = plans.findById(invitation.getPlanId()).orElseThrow(InvitationNotFoundException::new);
        if (!members.existsByPlanIdAndUserId(plan.getId(), userId)) {
            if (members.countByPlanId(plan.getId()) >= Plan.MAX_MEMBERS) {
                throw new PlanFullException();
            }
            members.save(new PlanMember(plan.getId(), userId));
        }
        return summary(plan);
    }

    private PlanInvitation activeInvitation(String token) {
        return invitations.findByToken(token).filter(invitation -> invitation.isActive(Instant.now()))
            .orElseThrow(InvitationNotFoundException::new);
    }

    private Plan createPlan(int userId, String name) {
        Plan plan = plans.save(new Plan(name, userId));
        members.save(new PlanMember(plan.getId(), userId));
        return plan;
    }

    private PlanSummary summary(Plan plan) {
        return new PlanSummary(plan, members.countByPlanId(plan.getId()));
    }

    private void requireUser(int userId) {
        if (!users.existsById(userId)) {
            throw new UserNotFoundException();
        }
    }

    /** 32 random bytes as URL-safe text: 256 bits, so it cannot be guessed. */
    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

}
