package com.compass.backend.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.compass.backend.exception.GoalNotFoundException;
import com.compass.backend.exception.UserNotFoundException;

/**
 * Maps failures on the goal endpoints to HTTP responses. Invalid fields use the same JSON body as
 * registration, {"errors": {"title": "..."}}, so the web page can show each message next to its field.
 * Limited to GoalController so the other endpoints keep their own error format.
 */
@RestControllerAdvice(assignableTypes = GoalController.class)
public class GoalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Map<String, String>>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
            .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("errors", errors));
    }

    /** The goal does not exist or is someone else's: the same 404 for both. */
    @ExceptionHandler(GoalNotFoundException.class)
    public ResponseEntity<String> handleGoalNotFound(GoalNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    /** A valid token for a deleted account: 401, so the web page drops the session. */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<String> handleUserNotFound(UserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage());
    }

}
