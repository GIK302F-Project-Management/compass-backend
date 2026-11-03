package com.compass.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.compass.backend.dto.RegisterRequest;
import com.compass.backend.dto.UserResponse;
import com.compass.backend.model.User;
import com.compass.backend.service.UserService;

import jakarta.validation.Valid;

@CrossOrigin
@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * @brief This method creates a new account.
     * @param request JSON with name, email (university domain only) and password (8 to 72 characters).
     * @return the new user, without password (201). Invalid fields (400) and an email that is
     *         already registered (409) are turned into responses by UserExceptionHandler.
     */
    @PostMapping(value = "/register", consumes = "application/json")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = userService.register(request.name(), request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
    }

    /**
     * @brief This method checks the email and password of a user.
     * @param credentials JSON with email and password.
     * @return OK with a welcome message when the credentials are valid. An unknown email (404) and a
     *         wrong password (401) are turned into responses by UserExceptionHandler.
     */
    @PostMapping(value = "/login", consumes = "application/json")
    public ResponseEntity<String> login(@RequestBody User credentials) {
        User user = userService.authenticate(credentials.getEmail(), credentials.getPassword());
        return ResponseEntity.ok("Welcome, " + user.getName() + "!");
    }

}
