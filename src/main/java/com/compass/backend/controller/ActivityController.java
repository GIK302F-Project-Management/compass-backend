package com.compass.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.compass.backend.dto.ActivityRequest;
import com.compass.backend.dto.ActivityResponse;
import com.compass.backend.dto.DoneRequest;
import com.compass.backend.dto.InvitationResponse;
import com.compass.backend.dto.MemberResponse;
import com.compass.backend.service.ActivityService;
import com.compass.backend.service.ProgressService;
import com.compass.backend.service.ProgressService.ActivityProgress;
import com.compass.backend.service.SharingService;

import jakarta.validation.Valid;

/**
 * The activities of the student's atlas: their own and the ones shared with them. Any member can see, edit and
 * complete an activity; only the owner deletes it, invites others and removes members. A student who is not a
 * member gets 404, as if the activity did not exist.
 */
@RestController
@RequestMapping("/activities")
public class ActivityController {

    private final ActivityService activityService;
    private final SharingService sharingService;
    private final ProgressService progressService;

    public ActivityController(ActivityService activityService, SharingService sharingService,
            ProgressService progressService) {
        this.activityService = activityService;
        this.sharingService = sharingService;
        this.progressService = progressService;
    }

    /**
     * @brief This method lists every activity in the student's atlas.
     * @return the activities, oldest first.
     */
    @GetMapping
    public List<ActivityResponse> list(@AuthenticationPrincipal Jwt jwt) {
        int userId = userId(jwt);
        return activityService.list(userId).stream().map(view -> ActivityResponse.from(view, userId)).toList();
    }

    /**
     * @brief This method adds an activity to the student's atlas, with the student as its owner.
     * @param request JSON with title, subject and dueDate (all required).
     * @return 201 with the created activity.
     */
    @PostMapping(consumes = "application/json")
    public ResponseEntity<ActivityResponse> create(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ActivityRequest request) {
        int userId = userId(jwt);
        ActivityResponse created = ActivityResponse.from(
            activityService.create(userId, request.title(), request.subject(), request.dueDate()), userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * @brief This method shows one activity.
     * @return the activity, or 404.
     */
    @GetMapping("/{id}")
    public ActivityResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable int id) {
        int userId = userId(jwt);
        return ActivityResponse.from(activityService.get(userId, id), userId);
    }

    /**
     * @brief This method replaces the title, subject and due date of an activity (any member).
     * @return the updated activity, or 404.
     */
    @PutMapping(value = "/{id}", consumes = "application/json")
    public ActivityResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable int id,
            @Valid @RequestBody ActivityRequest request) {
        int userId = userId(jwt);
        return ActivityResponse.from(
            activityService.update(userId, id, request.title(), request.subject(), request.dueDate()), userId);
    }

    /**
     * @brief This method completes or reopens an activity. Completing it completes its open tasks too.
     * @param request JSON with done (true or false).
     * @return the updated activity, or 404.
     */
    @PutMapping(value = "/{id}/done", consumes = "application/json")
    public ActivityResponse setDone(@AuthenticationPrincipal Jwt jwt, @PathVariable int id,
            @Valid @RequestBody DoneRequest request) {
        int userId = userId(jwt);
        return ActivityResponse.from(activityService.setDone(userId, id, request.done()), userId);
    }

    /**
     * @brief This method deletes an activity with its tasks, for every member (owner only).
     * @return 204; 403 when the student is a member but not the owner; 404.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable int id) {
        activityService.delete(userId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * @brief This method lists the members of an activity, the owner first.
     * @return the members, by name only.
     */
    @GetMapping("/{id}/members")
    public List<MemberResponse> members(@AuthenticationPrincipal Jwt jwt, @PathVariable int id) {
        int userId = userId(jwt);
        return sharingService.members(userId, id).stream().map(member -> MemberResponse.from(member, userId)).toList();
    }

    /**
     * @brief This method takes a student out of an activity: use your own id to leave, or (as the owner) a
     *        member's id. Their tasks stay, without an assignee.
     * @return 204; 409 when the owner tries to leave; 403 when a member removes someone else.
     */
    @DeleteMapping("/{id}/members/{memberId}")
    public ResponseEntity<Void> removeMember(@AuthenticationPrincipal Jwt jwt, @PathVariable int id,
            @PathVariable int memberId) {
        sharingService.removeMember(userId(jwt), id, memberId);
        return ResponseEntity.noContent().build();
    }

    /**
     * @brief This method creates an invitation link that works for a week (owner only).
     * @return 201 with the invitation and its token.
     */
    @PostMapping("/{id}/invitations")
    public ResponseEntity<InvitationResponse> invite(@AuthenticationPrincipal Jwt jwt, @PathVariable int id) {
        InvitationResponse created = InvitationResponse.from(sharingService.invite(userId(jwt), id));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * @brief This method lists the invitation links that still work (owner only).
     * @return the invitations, newest first.
     */
    @GetMapping("/{id}/invitations")
    public List<InvitationResponse> invitations(@AuthenticationPrincipal Jwt jwt, @PathVariable int id) {
        return sharingService.activeInvitations(userId(jwt), id).stream().map(InvitationResponse::from).toList();
    }

    /**
     * @brief This method revokes an invitation link (owner only).
     * @return 204, or 404 when the activity has no such invitation.
     */
    @DeleteMapping("/{id}/invitations/{invitationId}")
    public ResponseEntity<Void> revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable int id,
            @PathVariable int invitationId) {
        sharingService.revokeInvitation(userId(jwt), id, invitationId);
        return ResponseEntity.noContent().build();
    }

    /**
     * @brief This method shows how far the activity has got and how its tasks are shared out.
     * @return the progress, computed now; 404 when the student is not a member.
     */
    @GetMapping("/{id}/progress")
    public ActivityProgress progress(@AuthenticationPrincipal Jwt jwt, @PathVariable int id) {
        return progressService.of(userId(jwt), id);
    }

    private static int userId(Jwt jwt) {
        return Integer.parseInt(jwt.getSubject());
    }

}
