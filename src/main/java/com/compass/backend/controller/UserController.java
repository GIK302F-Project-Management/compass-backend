package com.compass.backend.controller;

import java.util.Map;

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
import com.compass.backend.model.UserRepository;
import com.compass.backend.service.PasswordHasher;

import jakarta.validation.Valid;

@CrossOrigin
@RestController
@RequestMapping("/user")
public class UserController {

    /**
     * @brief This method creates a new account.
     * @param request JSON with name, email (university domain only) and password (8 to 72 characters).
     * @return an HTTP response (created with the new user, without password, if everything is valid;
     *         bad request with the invalid fields, handled by UserExceptionHandler;
     *         conflict if the email is already registered)
     */
    @PostMapping(value = "/register", consumes = "application/json")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {

        User user = UserRepository.instance.register(
            request.name(), request.email(), PasswordHasher.hash(request.password()));

        if (user == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("errors", Map.of("email", "An account with this email already exists.")));
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
    }

    /**
     * @brief This method checks the email and password of a user.
     * @param credentials JSON with email and password.
     * @return an HTTP response (OK with a welcome message if the credentials are valid,
     *         not found if the email is not registered, unauthorized if the password is wrong)
     */
    @PostMapping(value = "/login", consumes = "application/json")
    public ResponseEntity<String> login(@RequestBody User credentials) {

        User user = UserRepository.instance.findByEmail(credentials.getEmail());

        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Sign Up first!");
        }

        if (!PasswordHasher.matches(credentials.getPassword(), user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Incorrect password!");
        }

        return ResponseEntity.ok("Welcome, " + user.getName() + "!");
    }

}
