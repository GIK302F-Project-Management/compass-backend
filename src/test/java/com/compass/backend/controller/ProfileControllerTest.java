package com.compass.backend.controller;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.compass.backend.model.User;
import com.compass.backend.model.UserRepository;
import com.compass.backend.service.PasswordHasher;
import com.compass.backend.service.TokenService;

/** The profile of the logged-in student: reading it and changing their name. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:sqlite:target/compass-test.db",
    "llm.enabled=false"
})
class ProfileControllerTest {

    private static final String DORA = "dora@alumni.mondragon.edu";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository users;

    @Autowired
    private JwtEncoder jwtEncoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        User dora = users.findByEmailIgnoreCase(DORA)
            .orElseGet(() -> users.save(new User(0, "Dora", DORA, PasswordHasher.hash("secret123"))));
        dora.setName("Dora"); // each test starts from the same name
        users.save(dora);
    }

    private String bearer() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(TokenService.ISSUER).subject(String.valueOf(users.findByEmailIgnoreCase(DORA).orElseThrow().getId()))
            .issuedAt(now).expiresAt(now.plus(1, ChronoUnit.HOURS)).build();
        return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    }

    @Test
    void theStudentChangesTheirNameButNotTheirEmail() throws Exception {
        mockMvc.perform(put("/user/me").header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"  Dora Silva \", \"email\": \"other@alumni.mondragon.edu\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Dora Silva"))
            .andExpect(jsonPath("$.email").value(DORA))
            .andExpect(jsonPath("$.password").doesNotExist());

        mockMvc.perform(get("/user/me").header(HttpHeaders.AUTHORIZATION, bearer()))
            .andExpect(jsonPath("$.name").value("Dora Silva"))
            .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void theNameIsValidatedLikeAtRegistration() throws Exception {
        mockMvc.perform(put("/user/me").header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"   \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.name").value("Please enter your name."));
        mockMvc.perform(put("/user/me").header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"" + "x".repeat(101) + "\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void changingTheProfileNeedsALogin() throws Exception {
        mockMvc.perform(put("/user/me").contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Mallory\"}"))
            .andExpect(status().isUnauthorized());
    }

}
