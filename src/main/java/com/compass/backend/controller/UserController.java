package com.compass.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.compass.backend.dto.LoginRequest;
import com.compass.backend.dto.LoginResponse;
import com.compass.backend.dto.ProfileRequest;
import com.compass.backend.dto.RegisterRequest;
import com.compass.backend.dto.RegisterResponse;
import com.compass.backend.dto.UserResponse;
import com.compass.backend.model.User;
import com.compass.backend.service.AccessToken;
import com.compass.backend.service.TokenService;
import com.compass.backend.service.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;
    private final TokenService tokenService;

    public UserController(UserService userService, TokenService tokenService) {
        this.userService = userService;
        this.tokenService = tokenService;
    }

    /**
     * @brief This method creates a new account.
     * @param request JSON with name, email (university domain only) and password (8 to 72 characters).
     * @return 202 with a fixed message, also when the email already has an account, so registration
     *         cannot be used to find out which emails are registered. Invalid fields (400) are turned
     *         into responses by UserExceptionHandler.
     */
    @PostMapping(value = "/register", consumes = "application/json")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        userService.register(request.name(), request.email(), request.password());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(RegisterResponse.received());
    }

    /**
     * @brief This method checks the email and password of a user and starts a session.
     * @param request JSON with email and password.
     * @return the access token (send it as "Authorization: Bearer ..."), when it expires and the user.
     *         Missing fields (400) and wrong credentials (401, same message for an unknown email and
     *         for a wrong password) are turned into responses by UserExceptionHandler.
     */
    @PostMapping(value = "/login", consumes = "application/json")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        User user = userService.authenticate(request.email(), request.password());
        AccessToken token = tokenService.issue(user);
        return new LoginResponse(token.value(), token.expiresAt(), UserResponse.from(user));
    }

    /**
     * @brief This method returns the user the access token belongs to.
     *        The web page uses it to confirm a stored session is still valid.
     * @param jwt the verified token; its subject is the user id.
     * @return the user, without password. A missing or invalid token gets 401 from the security filter.
     */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return UserResponse.from(userService.findById(Integer.parseInt(jwt.getSubject())));
    }

    /**
     * @brief This method changes the profile of the logged-in user (their name; the email is how they log in).
     * @param request JSON with name (required, at most 100 characters).
     * @return the updated user. Invalid fields get 400 with {"errors": {"name": "..."}}.
     */
    @PutMapping(value = "/me", consumes = "application/json")
    public UserResponse updateMe(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileRequest request) {
        return UserResponse.from(userService.rename(Integer.parseInt(jwt.getSubject()), request.name()));
    }

}
