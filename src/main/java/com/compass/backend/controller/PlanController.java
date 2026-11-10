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

import com.compass.backend.dto.InvitationResponse;
import com.compass.backend.dto.PlanDetailResponse;
import com.compass.backend.dto.PlanRequest;
import com.compass.backend.dto.PlanResponse;
import com.compass.backend.service.PlanService;
import com.compass.backend.service.ProgressService;
import com.compass.backend.service.ProgressService.PlanProgress;

import jakarta.validation.Valid;

/**
 * Study plans shared between students. All endpoints need an access token. A student only ever sees
 * the plans they are a member of; anything else answers 404, as if the plan did not exist.
 */
@RestController
@RequestMapping("/plans")
public class PlanController {

    private final PlanService planService;
    private final ProgressService progressService;

    public PlanController(PlanService planService, ProgressService progressService) {
        this.planService = planService;
        this.progressService = progressService;
    }

    /**
     * @brief This method lists the plans the student is in. A student with none gets a personal plan first.
     * @return the plans, in the order the student joined them.
     */
    @GetMapping
    public List<PlanResponse> list(@AuthenticationPrincipal Jwt jwt) {
        int userId = userId(jwt);
        return planService.list(userId).stream().map(summary -> PlanResponse.from(summary, userId)).toList();
    }

    /**
     * @brief This method creates a plan owned by the student.
     * @param request JSON with name (required, at most 100 characters).
     * @return 201 with the created plan.
     */
    @PostMapping(consumes = "application/json")
    public ResponseEntity<PlanResponse> create(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PlanRequest request) {
        int userId = userId(jwt);
        PlanResponse created = PlanResponse.from(planService.create(userId, request.name()), userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * @brief This method shows a plan with its members.
     * @return the plan, or 404 when the student is not a member.
     */
    @GetMapping("/{id}")
    public PlanDetailResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable int id) {
        int userId = userId(jwt);
        return PlanDetailResponse.from(planService.get(userId, id), userId);
    }

    /**
     * @brief This method renames a plan.
     * @return the renamed plan; 403 when the student is a member but not the owner.
     */
    @PutMapping(value = "/{id}", consumes = "application/json")
    public PlanResponse rename(@AuthenticationPrincipal Jwt jwt, @PathVariable int id,
            @Valid @RequestBody PlanRequest request) {
        int userId = userId(jwt);
        return PlanResponse.from(planService.rename(userId, id, request.name()), userId);
    }

    /**
     * @brief This method deletes a plan with everything in it.
     * @return 204; 403 when the student is a member but not the owner.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable int id) {
        planService.delete(userId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * @brief This method takes a student out of a plan: use your own id to leave, or (as the owner) a member's id.
     * @return 204; 409 when the owner tries to leave; 403 when a member removes someone else.
     */
    @DeleteMapping("/{id}/members/{memberId}")
    public ResponseEntity<Void> removeMember(@AuthenticationPrincipal Jwt jwt, @PathVariable int id,
            @PathVariable int memberId) {
        planService.removeMember(userId(jwt), id, memberId);
        return ResponseEntity.noContent().build();
    }

    /**
     * @brief This method creates an invitation link that works for a week (owner only).
     * @return 201 with the invitation and its token.
     */
    @PostMapping("/{id}/invitations")
    public ResponseEntity<InvitationResponse> invite(@AuthenticationPrincipal Jwt jwt, @PathVariable int id) {
        InvitationResponse created = InvitationResponse.from(planService.invite(userId(jwt), id));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * @brief This method lists the invitation links that still work (owner only).
     * @return the invitations, newest first.
     */
    @GetMapping("/{id}/invitations")
    public List<InvitationResponse> invitations(@AuthenticationPrincipal Jwt jwt, @PathVariable int id) {
        return planService.activeInvitations(userId(jwt), id).stream().map(InvitationResponse::from).toList();
    }

    /**
     * @brief This method revokes an invitation link (owner only).
     * @return 204, or 404 when the plan has no such invitation.
     */
    @DeleteMapping("/{id}/invitations/{invitationId}")
    public ResponseEntity<Void> revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable int id,
            @PathVariable int invitationId) {
        planService.revokeInvitation(userId(jwt), id, invitationId);
        return ResponseEntity.noContent().build();
    }

    /**
     * @brief This method shows the overall progress of a plan and what each member completed.
     * @return the progress, computed now; 404 when the student is not a member.
     */
    @GetMapping("/{id}/progress")
    public PlanProgress progress(@AuthenticationPrincipal Jwt jwt, @PathVariable int id) {
        return progressService.of(userId(jwt), id);
    }

    private static int userId(Jwt jwt) {
        return Integer.parseInt(jwt.getSubject());
    }

}
