package com.compass.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.compass.backend.model.UserRepository;

/**
 * Login tests against the sample users added by SampleUsersSeeder, without starting the server.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite:target/compass-test.db")
class UserControllerTest {

    @Autowired
    private UserRepository users;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(users)).build();
    }

    private String credentials(String email, String password) {
        return "{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}";
    }

    @Test
    void loginSucceedsWithCorrectCredentials() throws Exception {
        mockMvc.perform(post("/user/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials("ana@alumni.mondragon.edu", "ana123")))
                .andExpect(status().isOk())
                .andExpect(content().string("Welcome, Ana!"));
    }

    @Test
    void loginFailsWithWrongPassword() throws Exception {
        mockMvc.perform(post("/user/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials("ana@alumni.mondragon.edu", "wrong")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Incorrect password!"));
    }

    @Test
    void loginFailsWhenAccountDoesNotExist() throws Exception {
        mockMvc.perform(post("/user/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials("nobody@alumni.mondragon.edu", "ana123")))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Sign Up first!"));
    }

}
