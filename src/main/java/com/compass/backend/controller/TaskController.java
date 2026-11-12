package com.compass.backend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.compass.backend.dto.TaskRequest;
import com.compass.backend.dto.TaskResponse;
import com.compass.backend.service.TaskChanges;
import com.compass.backend.service.TaskService;

import jakarta.validation.Valid;

/**
 * The tasks of an activity. Any member of the activity can list, add, change, assign, complete and
 * delete them; a student who is not a member gets 404.
 */
@RestController
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * @brief This method lists the tasks of an activity.
     * @return the tasks, oldest first.
     */
    @GetMapping("/activities/{activityId}/tasks")
    public List<TaskResponse> listByActivity(@AuthenticationPrincipal Jwt jwt, @PathVariable int activityId) {
        return taskService.listByActivity(userId(jwt), activityId).stream().map(TaskResponse::from).toList();
    }

    /**
     * @brief This method lists every task of every activity in the student's atlas, so a page (the route planner)
     *        can load them with one request. Each task says who it is assigned to.
     * @return the tasks, oldest first.
     */
    @GetMapping("/tasks")
    public List<TaskResponse> listForMember(@AuthenticationPrincipal Jwt jwt) {
        return taskService.listForMember(userId(jwt)).stream().map(TaskResponse::from).toList();
    }

    /**
     * @brief This method adds a task to an activity, assigned to the student who adds it.
     * @param request JSON with title (required), details, dueDate and estimatedMinutes (all optional).
     * @return 201 with the created task.
     */
    @PostMapping(value = "/activities/{activityId}/tasks", consumes = "application/json")
    public ResponseEntity<TaskResponse> create(@AuthenticationPrincipal Jwt jwt, @PathVariable int activityId,
            @Valid @RequestBody TaskRequest request) {
        TaskResponse created = TaskResponse.from(taskService.create(userId(jwt), activityId, request.title(),
            request.details(), request.dueDate(), request.estimatedMinutes()));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * @brief This method changes some fields of a task (title, details, dueDate, estimatedMinutes, assigneeId,
     *        done). Fields that are not in the body stay as they are; null clears dueDate, estimatedMinutes or
     *        assigneeId. The assignee must be a member of the activity.
     * @param body JSON object with the fields to change.
     * @return the updated task, 400 for an invalid field, or 404.
     */
    @PatchMapping(value = "/tasks/{id}", consumes = "application/json")
    public TaskResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable int id,
            @RequestBody Map<String, Object> body) {
        return TaskResponse.from(taskService.update(userId(jwt), id, TaskChanges.from(body)));
    }

    /**
     * @brief This method deletes a task.
     * @return 204, or 404.
     */
    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable int id) {
        taskService.delete(userId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    private static int userId(Jwt jwt) {
        return Integer.parseInt(jwt.getSubject());
    }

}
