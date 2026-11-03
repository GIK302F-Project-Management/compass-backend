package com.compass.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.compass.backend.model.User;
import com.compass.backend.model.UserRepository;
import com.compass.backend.service.PasswordHasher;
import com.compass.backend.service.TokenService;
import com.compass.backend.service.UserService;

/**
 * Login tests against a known account (Ana) that the test creates, without starting the server.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite:target/compass-test.db")
class UserControllerTest {

    @Autowired
    private UserRepository users;

    @Autowired
    private UserService userService;

    @Autowired
    private TokenService tokenService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ensureAnaExists();
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService, tokenService))
                .setControllerAdvice(new UserExceptionHandler())
                .build();
    }

    /** The tests need one known account, so they create it instead of relying on seeded data. */
    private void ensureAnaExists() {
        if (!users.existsByEmailIgnoreCase("ana@alumni.mondragon.edu")) {
            users.save(new User(0, "Ana", "ana@alumni.mondragon.edu", PasswordHasher.hash("ana123")));
        }
    }

    private String credentials(String email, String password) {
        return "{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}";
    }

    @Test
    void loginReturnsTokenAndUserWithoutPassword() throws Exception {
        mockMvc.perform(post("/user/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials("ana@alumni.mondragon.edu", "ana123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").exists())
                .andExpect(jsonPath("$.user.name").value("Ana"))
                .andExpect(jsonPath("$.user.email").value("ana@alumni.mondragon.edu"))
                .andExpect(jsonPath("$.user.password").doesNotExist());
    }

    @Test
    void loginIgnoresEmailCaseAndSurroundingSpaces() throws Exception {
        mockMvc.perform(post("/user/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials("  ANA@alumni.mondragon.edu ", "ana123")))
                .andExpect(status().isOk());
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameAnswer() throws Exception {
        String wrongPassword = mockMvc.perform(post("/user/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials("ana@alumni.mondragon.edu", "wrong")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(post("/user/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials("nobody@alumni.mondragon.edu", "ana123")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(wrongPassword));
    }

    @Test
    void loginRequiresEmailAndPassword() throws Exception {
        mockMvc.perform(post("/user/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"\", \"password\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

}
