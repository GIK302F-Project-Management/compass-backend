package com.compass.backend.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.compass.backend.exception.ActivityNotFoundException;
import com.compass.backend.exception.InvalidTaskChangesException;
import com.compass.backend.exception.InvitationNotFoundException;
import com.compass.backend.exception.MemberNotFoundException;
import com.compass.backend.exception.NotActivityOwnerException;
import com.compass.backend.exception.OwnerCannotLeaveException;
import com.compass.backend.exception.ActivityFullException;
import com.compass.backend.exception.TaskNotFoundException;
import com.compass.backend.exception.UserNotFoundException;

/**
 * Maps failures on the activity, sharing, invitation and task endpoints to HTTP responses. Invalid fields
 * use the same JSON body as registration, {"errors": {"title": "..."}}, so the web page can show each
 * message next to its field. Limited to these controllers so the other endpoints keep their own format.
 */
@RestControllerAdvice(assignableTypes = {
    InvitationController.class, ActivityController.class, TaskController.class
})
public class CollaborationExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Map<String, String>>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
            .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("errors", errors));
    }

    @ExceptionHandler(InvalidTaskChangesException.class)
    public ResponseEntity<Map<String, Map<String, String>>> handleInvalidChanges(InvalidTaskChangesException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("errors", ex.getErrors()));
    }

    /** The thing does not exist or is not the student's: the same 404 for both. */
    @ExceptionHandler({
        ActivityNotFoundException.class, TaskNotFoundException.class,
        InvitationNotFoundException.class, MemberNotFoundException.class
    })
    public ResponseEntity<String> handleNotFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    /** A member who is not the owner tried an owner-only action. */
    @ExceptionHandler(NotActivityOwnerException.class)
    public ResponseEntity<String> handleNotOwner(NotActivityOwnerException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
    }

    @ExceptionHandler({ ActivityFullException.class, OwnerCannotLeaveException.class })
    public ResponseEntity<String> handleConflict(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    /** A valid token for a deleted account: 401, so the web page drops the session. */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<String> handleUserNotFound(UserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage());
    }

}
