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
import org.springframework.web.bind.annotation.RestController;

import com.compass.backend.dto.ActivityRequest;
import com.compass.backend.dto.ActivityResponse;
import com.compass.backend.dto.DoneRequest;
import com.compass.backend.service.ActivityService;

import jakarta.validation.Valid;

/**
 * The activities of a plan. Any member of the plan can list, add, edit, complete and delete them;
 * a student who is not a member gets 404.
 */
@RestController
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    /**
     * @brief This method lists the activities of a plan.
     * @return the activities, oldest first.
     */
    @GetMapping("/plans/{planId}/activities")
    public List<ActivityResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable int planId) {
        return activityService.list(userId(jwt), planId).stream().map(ActivityResponse::from).toList();
    }

    /**
     * @brief This method adds an activity to a plan.
     * @param request JSON with title, subject and dueDate (all required).
     * @return 201 with the created activity.
     */
    @PostMapping(value = "/plans/{planId}/activities", consumes = "application/json")
    public ResponseEntity<ActivityResponse> create(@AuthenticationPrincipal Jwt jwt, @PathVariable int planId,
            @Valid @RequestBody ActivityRequest request) {
        ActivityResponse created = ActivityResponse.from(
            activityService.create(userId(jwt), planId, request.title(), request.subject(), request.dueDate()));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * @brief This method shows one activity.
     * @return the activity, or 404.
     */
    @GetMapping("/activities/{id}")
    public ActivityResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable int id) {
        return ActivityResponse.from(activityService.get(userId(jwt), id));
    }

    /**
     * @brief This method replaces the title, subject and due date of an activity.
     * @return the updated activity, or 404.
     */
    @PutMapping(value = "/activities/{id}", consumes = "application/json")
    public ActivityResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable int id,
            @Valid @RequestBody ActivityRequest request) {
        return ActivityResponse.from(
            activityService.update(userId(jwt), id, request.title(), request.subject(), request.dueDate()));
    }

    /**
     * @brief This method completes or reopens an activity. Completing it completes its open tasks too.
     * @param request JSON with done (true or false).
     * @return the updated activity, or 404.
     */
    @PutMapping(value = "/activities/{id}/done", consumes = "application/json")
    public ActivityResponse setDone(@AuthenticationPrincipal Jwt jwt, @PathVariable int id,
            @Valid @RequestBody DoneRequest request) {
        return ActivityResponse.from(activityService.setDone(userId(jwt), id, request.done()));
    }

    /**
     * @brief This method deletes an activity with its tasks.
     * @return 204, or 404.
     */
    @DeleteMapping("/activities/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable int id) {
        activityService.delete(userId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    private static int userId(Jwt jwt) {
        return Integer.parseInt(jwt.getSubject());
    }

}
