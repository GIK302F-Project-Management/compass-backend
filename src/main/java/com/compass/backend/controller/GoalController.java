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

import com.compass.backend.dto.GoalRequest;
import com.compass.backend.dto.GoalResponse;
import com.compass.backend.service.GoalService;

import jakarta.validation.Valid;

/**
 * The student's learning goals. All endpoints need an access token and only ever see the goals of
 * the student the token belongs to (the token's subject is the user id).
 */
@RestController
@RequestMapping("/goals")
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    /**
     * @brief This method lists the logged-in student's goals.
     * @return the goals, oldest first (new ones go at the end).
     */
    @GetMapping
    public List<GoalResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return goalService.list(userId(jwt)).stream().map(GoalResponse::from).toList();
    }

    /**
     * @brief This method creates a goal.
     * @param request JSON with title (required, at most 100 characters) and description (optional, at most 1000).
     * @return 201 with the created goal. Invalid fields (400) are turned into responses by GoalExceptionHandler.
     */
    @PostMapping(consumes = "application/json")
    public ResponseEntity<GoalResponse> create(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody GoalRequest request) {
        GoalResponse created = GoalResponse.from(goalService.create(userId(jwt), request.title(), request.description()));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * @brief This method replaces the title and description of a goal.
     * @param id goal id.
     * @param request JSON with title and description, same rules as when creating.
     * @return the updated goal, or 404 when the goal does not exist or is not the student's.
     */
    @PutMapping(value = "/{id}", consumes = "application/json")
    public GoalResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable int id,
            @Valid @RequestBody GoalRequest request) {
        return GoalResponse.from(goalService.update(userId(jwt), id, request.title(), request.description()));
    }

    /**
     * @brief This method deletes a goal.
     * @param id goal id.
     * @return 204, or 404 when the goal does not exist or is not the student's.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable int id) {
        goalService.delete(userId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    private static int userId(Jwt jwt) {
        return Integer.parseInt(jwt.getSubject());
    }

}
