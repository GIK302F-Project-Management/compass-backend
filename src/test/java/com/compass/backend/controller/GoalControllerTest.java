package com.compass.backend.controller;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.compass.backend.model.GoalRepository;
import com.compass.backend.model.User;
import com.compass.backend.model.UserRepository;
import com.compass.backend.service.PasswordHasher;
import com.compass.backend.service.TokenService;
import com.jayway.jsonpath.JsonPath;

/**
 * Learning goals through the real security filter chain: what a student can create, change and
 * delete, and that they can never see or touch another student's goals.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:sqlite:target/compass-test.db",
    "llm.enabled=false"
})
class GoalControllerTest {

    private static final String ANA = "ana@alumni.mondragon.edu";
    private static final String BEN = "ben@alumni.mondragon.edu";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository users;

    @Autowired
    private GoalRepository goals;

    @Autowired
    private JwtEncoder jwtEncoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        ensureUserExists("Ana", ANA);
        ensureUserExists("Ben", BEN);
        goals.deleteAll(); // each test starts with no goals
    }

    private void ensureUserExists(String name, String email) {
        if (!users.existsByEmailIgnoreCase(email)) {
            users.save(new User(0, name, email, PasswordHasher.hash("secret123")));
        }
    }

    /** A valid access token for the given user, issued the same way a login does. */
    private String bearer(String email) {
        int id = users.findByEmailIgnoreCase(email).orElseThrow().getId();
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(TokenService.ISSUER).subject(String.valueOf(id))
            .issuedAt(now).expiresAt(now.plus(1, ChronoUnit.HOURS)).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
        return "Bearer " + token;
    }

    private String body(String title, String description) {
        return "{\"title\": \"" + title + "\", \"description\": \"" + description + "\"}";
    }

    /** Creates a goal for the given user and returns its id. */
    private int createGoal(String email, String title, String description) throws Exception {
        String response = mockMvc.perform(post("/goals").header(HttpHeaders.AUTHORIZATION, bearer(email))
                .contentType(MediaType.APPLICATION_JSON).content(body(title, description)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    // ---------- login required ----------

    @Test
    void everyGoalEndpointNeedsALogin() throws Exception {
        mockMvc.perform(get("/goals")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/goals").contentType(MediaType.APPLICATION_JSON).content(body("Goal", "")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/goals/1").contentType(MediaType.APPLICATION_JSON).content(body("Goal", "")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/goals/1")).andExpect(status().isUnauthorized());
    }

    // ---------- create ----------

    @Test
    void createSavesTheGoalAndReturnsIt() throws Exception {
        mockMvc.perform(post("/goals").header(HttpHeaders.AUTHORIZATION, bearer(ANA))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Pass the Java exam", "Review all the lectures")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("Pass the Java exam"))
                .andExpect(jsonPath("$.description").value("Review all the lectures"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.userId").doesNotExist());

        mockMvc.perform(get("/goals").header(HttpHeaders.AUTHORIZATION, bearer(ANA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Pass the Java exam"));
    }

    @Test
    void createTrimsTheTextAndAcceptsAMissingDescription() throws Exception {
        mockMvc.perform(post("/goals").header(HttpHeaders.AUTHORIZATION, bearer(ANA))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"  Learn SQL  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Learn SQL"))
                .andExpect(jsonPath("$.description").value(""));
    }

    @Test
    void createRequiresATitle() throws Exception {
        mockMvc.perform(post("/goals").header(HttpHeaders.AUTHORIZATION, bearer(ANA))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("   ", "No title")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").value("Please give the goal a title."));

        mockMvc.perform(post("/goals").header(HttpHeaders.AUTHORIZATION, bearer(ANA))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists());
    }

    @Test
    void createRefusesTextsThatAreTooLong() throws Exception {
        mockMvc.perform(post("/goals").header(HttpHeaders.AUTHORIZATION, bearer(ANA))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("t".repeat(101), "d".repeat(1001))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors.description").exists());

        mockMvc.perform(post("/goals").header(HttpHeaders.AUTHORIZATION, bearer(ANA))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("t".repeat(100), "d".repeat(1000))))
                .andExpect(status().isCreated());
    }

    // ---------- list ----------

    @Test
    void listIsEmptyForAStudentWithoutGoals() throws Exception {
        mockMvc.perform(get("/goals").header(HttpHeaders.AUTHORIZATION, bearer(ANA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listKeepsTheOrderTheGoalsWereCreatedIn() throws Exception {
        createGoal(ANA, "First", "");
        createGoal(ANA, "Second", "");
        createGoal(ANA, "Third", "");

        mockMvc.perform(get("/goals").header(HttpHeaders.AUTHORIZATION, bearer(ANA)))
                .andExpect(jsonPath("$[0].title").value("First"))
                .andExpect(jsonPath("$[1].title").value("Second"))
                .andExpect(jsonPath("$[2].title").value("Third"));
    }

    // ---------- edit ----------

    @Test
    void updateChangesTitleAndDescription() throws Exception {
        int id = createGoal(ANA, "Old title", "Old description");

        mockMvc.perform(put("/goals/" + id).header(HttpHeaders.AUTHORIZATION, bearer(ANA))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("New title", "New description")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.title").value("New title"))
                .andExpect(jsonPath("$.description").value("New description"));

        mockMvc.perform(get("/goals").header(HttpHeaders.AUTHORIZATION, bearer(ANA)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("New title"));
    }

    @Test
    void updateWithAnInvalidTitleChangesNothing() throws Exception {
        int id = createGoal(ANA, "Keep me", "Still here");

        mockMvc.perform(put("/goals/" + id).header(HttpHeaders.AUTHORIZATION, bearer(ANA))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("", "Changed")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists());

        mockMvc.perform(get("/goals").header(HttpHeaders.AUTHORIZATION, bearer(ANA)))
                .andExpect(jsonPath("$[0].title").value("Keep me"))
                .andExpect(jsonPath("$[0].description").value("Still here"));
    }

    @Test
    void updateOfAGoalThatDoesNotExistGives404() throws Exception {
        mockMvc.perform(put("/goals/999999").header(HttpHeaders.AUTHORIZATION, bearer(ANA))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Goal", "")))
                .andExpect(status().isNotFound());
    }

    // ---------- delete ----------

    @Test
    void deleteRemovesTheGoal() throws Exception {
        int id = createGoal(ANA, "Delete me", "");
        createGoal(ANA, "Keep me", "");

        mockMvc.perform(delete("/goals/" + id).header(HttpHeaders.AUTHORIZATION, bearer(ANA)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/goals").header(HttpHeaders.AUTHORIZATION, bearer(ANA)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Keep me"));

        mockMvc.perform(delete("/goals/" + id).header(HttpHeaders.AUTHORIZATION, bearer(ANA)))
                .andExpect(status().isNotFound()); // already gone
    }

    // ---------- only your own goals ----------

    @Test
    void aStudentOnlySeesTheirOwnGoals() throws Exception {
        createGoal(ANA, "Ana's goal", "");
        createGoal(BEN, "Ben's goal", "");

        mockMvc.perform(get("/goals").header(HttpHeaders.AUTHORIZATION, bearer(ANA)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Ana's goal"));

        mockMvc.perform(get("/goals").header(HttpHeaders.AUTHORIZATION, bearer(BEN)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Ben's goal"));
    }

    @Test
    void aStudentCannotEditSomeoneElsesGoal() throws Exception {
        int anasGoal = createGoal(ANA, "Ana's goal", "Private");

        mockMvc.perform(put("/goals/" + anasGoal).header(HttpHeaders.AUTHORIZATION, bearer(BEN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Hacked", "Hacked")))
                .andExpect(status().isNotFound()); // same answer as a goal that does not exist

        mockMvc.perform(get("/goals").header(HttpHeaders.AUTHORIZATION, bearer(ANA)))
                .andExpect(jsonPath("$[0].title").value("Ana's goal"))
                .andExpect(jsonPath("$[0].description").value("Private"));
    }

    @Test
    void aStudentCannotDeleteSomeoneElsesGoal() throws Exception {
        int anasGoal = createGoal(ANA, "Ana's goal", "");

        mockMvc.perform(delete("/goals/" + anasGoal).header(HttpHeaders.AUTHORIZATION, bearer(BEN)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/goals").header(HttpHeaders.AUTHORIZATION, bearer(ANA)))
                .andExpect(jsonPath("$.length()").value(1));
    }

}
