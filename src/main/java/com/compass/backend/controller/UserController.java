package com.compass.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.compass.backend.model.User;
import com.compass.backend.model.UserRepository;

@CrossOrigin
@RestController
@RequestMapping("/user")
public class UserController {

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

        if (!user.getPassword().equals(credentials.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Incorrect password!");
        }

        return ResponseEntity.ok("Welcome, " + user.getName() + "!");
    }

}
