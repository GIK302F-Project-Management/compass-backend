package com.compass.backend.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.compass.backend.dto.ActivityResponse;
import com.compass.backend.service.SharingService;
import com.compass.backend.service.SharingService.InvitationPreview;

/**
 * What a student does with an invitation link: see what it is for, then accept it. The student has
 * to be logged in; an unknown, expired or revoked token always answers 404.
 */
@RestController
@RequestMapping("/invitations")
public class InvitationController {

    private final SharingService sharingService;

    public InvitationController(SharingService sharingService) {
        this.sharingService = sharingService;
    }

    /**
     * @brief This method shows the activity an invitation is for, without joining it.
     * @param token the token from the link.
     * @return the activity title, subject and due date, its owner, the member count and whether the student
     *         is already in it.
     */
    @GetMapping("/{token}")
    public InvitationPreview preview(@AuthenticationPrincipal Jwt jwt, @PathVariable String token) {
        return sharingService.preview(userId(jwt), token);
    }

    /**
     * @brief This method joins the activity, so it shows up in the student's atlas. Accepting twice is fine.
     * @param token the token from the link.
     * @return the activity; 409 when it is full.
     */
    @PostMapping("/{token}/accept")
    public ActivityResponse accept(@AuthenticationPrincipal Jwt jwt, @PathVariable String token) {
        int userId = userId(jwt);
        return ActivityResponse.from(sharingService.accept(userId, token), userId);
    }

    private static int userId(Jwt jwt) {
        return Integer.parseInt(jwt.getSubject());
    }

}
