package com.compass.backend.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.compass.backend.model.User;
import com.compass.backend.model.UserRepository;
import com.compass.backend.service.PasswordHasher;
import com.compass.backend.service.TokenService;
import com.compass.backend.service.UserService;

/**
 * Registration tests (POST /user/register) without starting the server.
 * They run against a separate SQLite file (target/compass-test.db). Every test registers unique emails
 * and removes them afterwards.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite:target/compass-test.db")
class UserRegistrationTest {

    @Autowired
    private UserRepository users;

    @Autowired
    private UserService userService;

    @Autowired
    private TokenService tokenService;

    private static final String DOMAIN = "@alumni.mondragon.edu";
    private static final String VALID_PASSWORD = "secret-pass";

    private MockMvc mockMvc;
    private final List<String> createdEmails = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ensureAnaExists();
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService, tokenService))
                .setControllerAdvice(new UserExceptionHandler())
                .build();
    }

    @AfterEach
    void removeCreatedUsers() {
        for (String email : createdEmails) {
            users.findByEmailIgnoreCase(email).ifPresent(users::delete);
        }
    }

    /** The tests need one known account, so they create it instead of relying on seeded data. */
    private void ensureAnaExists() {
        if (!users.existsByEmailIgnoreCase("ana@alumni.mondragon.edu")) {
            users.save(new User(0, "Ana", "ana@alumni.mondragon.edu", PasswordHasher.hash("ana123")));
        }
    }

    private String uniqueEmail() {
        String email = "test-" + UUID.randomUUID().toString().substring(0, 8) + DOMAIN;
        createdEmails.add(email);
        return email;
    }

    private static String json(String name, String email, String password) {
        return "{\"name\": " + quote(name) + ", \"email\": " + quote(email) + ", \"password\": " + quote(password) + "}";
    }

    private static String quote(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private ResultActions register(String body) throws Exception {
        return mockMvc.perform(post("/user/register").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/user/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}"));
    }

    // ---------- success ----------

    @Test
    void registerCreatesTheUserAndReturnsNoAccountData() throws Exception {
        String email = uniqueEmail();

        register(json("Carla", email, VALID_PASSWORD))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());

        assertNotNull(users.findByEmailIgnoreCase(email).orElse(null));
    }

    @Test
    void registeredUserCanLogIn() throws Exception {
        String email = uniqueEmail();
        register(json("Carla", email, VALID_PASSWORD)).andExpect(status().isAccepted());

        login(email, VALID_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.name").value("Carla"))
                .andExpect(jsonPath("$.token").isNotEmpty());
        login(email, "wrong-password")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordIsStoredAsBcryptHash() throws Exception {
        String email = uniqueEmail();
        register(json("Carla", email, VALID_PASSWORD)).andExpect(status().isAccepted());

        String stored = users.findByEmailIgnoreCase(email).orElse(null).getPassword();
        assertNotEquals(VALID_PASSWORD, stored);
        assertTrue(stored.startsWith("$2"), "expected a BCrypt hash but got: " + stored);
    }

    @Test
    void sampleUsersPasswordsAreAlsoHashed() {
        User ana = users.findByEmailIgnoreCase("ana@alumni.mondragon.edu").orElseThrow();
        assertNotEquals("ana123", ana.getPassword());
    }

    @Test
    void eachNewUserGetsADifferentId() throws Exception {
        String first = uniqueEmail();
        String second = uniqueEmail();
        register(json("One", first, VALID_PASSWORD)).andExpect(status().isAccepted());
        register(json("Two", second, VALID_PASSWORD)).andExpect(status().isAccepted());

        int firstId = users.findByEmailIgnoreCase(first).orElseThrow().getId();
        int secondId = users.findByEmailIgnoreCase(second).orElseThrow().getId();
        assertNotEquals(firstId, secondId);
    }

    @Test
    void emailIsTrimmedAndLowercasedAndNameIsTrimmed() throws Exception {
        String email = uniqueEmail();

        register(json("  Carla  ", "  " + email.toUpperCase().replace("ALUMNI.MONDRAGON.EDU", "Alumni.Mondragon.edu") + " ", VALID_PASSWORD))
                .andExpect(status().isAccepted());

        User stored = users.findByEmailIgnoreCase(email).orElseThrow();
        assertEquals("Carla", stored.getName());
        assertEquals(email, stored.getEmail());
    }

    @Test
    void passwordWithSpacesIsAcceptedAsIs() throws Exception {
        String email = uniqueEmail();
        register(json("Carla", email, "  spaced pass  ")).andExpect(status().isAccepted());

        login(email, "  spaced pass  ").andExpect(status().isOk());
        login(email, "spaced pass").andExpect(status().isUnauthorized());
    }

    @Test
    void passwordOfExactlyEightAndSeventyTwoCharactersIsAccepted() throws Exception {
        register(json("Min", uniqueEmail(), "a".repeat(8))).andExpect(status().isAccepted());
        register(json("Max", uniqueEmail(), "a".repeat(72))).andExpect(status().isAccepted());
    }

    // ---------- duplicates ----------

    @Test
    void registeringATakenEmailLooksExactlyLikeASuccessAndChangesNothing() throws Exception {
        String email = uniqueEmail();
        String firstAnswer = register(json("Carla", email, VALID_PASSWORD))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        String secondAnswer = register(json("Other Carla", email, "another-pass"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        assertEquals(firstAnswer, secondAnswer);

        // the original account is untouched: same name, and the old password still works, the new one does not
        assertEquals("Carla", users.findByEmailIgnoreCase(email).orElse(null).getName());
        login(email, VALID_PASSWORD).andExpect(status().isOk());
        login(email, "another-pass").andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateEmailIsDetectedRegardlessOfCase() throws Exception {
        String email = uniqueEmail();
        register(json("Carla", email, VALID_PASSWORD)).andExpect(status().isAccepted());

        register(json("Impostor", email.toUpperCase().replace("ALUMNI.MONDRAGON.EDU", "alumni.mondragon.edu"), "another-pass"))
                .andExpect(status().isAccepted());

        assertEquals("Carla", users.findByEmailIgnoreCase(email).orElseThrow().getName());
    }

    @Test
    void sampleUserEmailCannotBeTakenOver() throws Exception {
        register(json("Fake Ana", "ana@alumni.mondragon.edu", "another-pass"))
                .andExpect(status().isAccepted());

        assertEquals("Ana", users.findByEmailIgnoreCase("ana@alumni.mondragon.edu").orElseThrow().getName());
        login("ana@alumni.mondragon.edu", "another-pass").andExpect(status().isUnauthorized());
    }

    @Test
    void requestToStringHidesThePassword() {
        String text = new com.compass.backend.dto.RegisterRequest("Carla", "c@alumni.mondragon.edu", "plain-text-pw").toString();

        assertTrue(!text.contains("plain-text-pw"), text);
    }

    // ---------- validation: name ----------

    @ParameterizedTest
    @ValueSource(strings = { "", "   " })
    void blankNameIsRejected(String name) throws Exception {
        String email = uniqueEmail();
        register(json(name, email, VALID_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").value("Please enter your name."));
        assertNull(users.findByEmailIgnoreCase(email).orElse(null));
    }

    @Test
    void missingNameIsRejected() throws Exception {
        register("{\"email\": \"" + uniqueEmail() + "\", \"password\": \"" + VALID_PASSWORD + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void nameLongerThan100CharactersIsRejected() throws Exception {
        register(json("a".repeat(101), uniqueEmail(), VALID_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    // ---------- validation: email ----------

    @Test
    void blankOrMissingEmailIsRejected() throws Exception {
        register(json("Carla", "", VALID_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());
        register("{\"name\": \"Carla\", \"password\": \"" + VALID_PASSWORD + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "carla@gmail.com",
        "carla@mondragon.edu",
        "carla@alumni.mondragon.edu.evil.com",
        "carla@evil.com@alumni.mondragon.edu",
        "carla@alumniXmondragonXedu",
        "carla@sub.alumni.mondragon.edu",
        "alumni.mondragon.edu",
        "@alumni.mondragon.edu",
        "car la@alumni.mondragon.edu",
        "carla"
    })
    void nonUniversityOrMalformedEmailIsRejected(String email) throws Exception {
        register(json("Carla", email, VALID_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").value(containsString(DOMAIN.substring(1))));
        assertNull(users.findByEmailIgnoreCase(email).orElse(null));
    }

    // ---------- validation: password ----------

    @ParameterizedTest
    @ValueSource(strings = { "", "1234567", "short" })
    void passwordShorterThanEightCharactersIsRejected(String password) throws Exception {
        String email = uniqueEmail();
        register(json("Carla", email, password))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
        assertNull(users.findByEmailIgnoreCase(email).orElse(null));
    }

    @Test
    void passwordLongerThan72CharactersIsRejected() throws Exception {
        register(json("Carla", uniqueEmail(), "a".repeat(73)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void passwordOver72BytesButUnder72CharactersIsRejectedInsteadOfFailing() throws Exception {
        // 40 characters, but 80 bytes in UTF-8
        register(json("Carla", uniqueEmail(), "é".repeat(40)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void missingPasswordIsRejected() throws Exception {
        register(json("Carla", uniqueEmail(), null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    // ---------- validation: several errors / bad bodies ----------

    @Test
    void allInvalidFieldsAreReportedTogether() throws Exception {
        register(json("", "carla@gmail.com", "123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void invalidRequestNeverCreatesAUser() throws Exception {
        long before = users.count();
        register(json("", "carla@gmail.com", "123")).andExpect(status().isBadRequest());
        assertEquals(before, users.count());
    }

    @Test
    void malformedJsonIsRejected() throws Exception {
        register("{not json").andExpect(status().isBadRequest());
    }

    @Test
    void emptyBodyIsRejected() throws Exception {
        mockMvc.perform(post("/user/register").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonJsonContentTypeIsRejected() throws Exception {
        mockMvc.perform(post("/user/register").contentType(MediaType.TEXT_PLAIN)
                .content(json("Carla", uniqueEmail(), VALID_PASSWORD)))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void errorResponsesNeverEchoThePassword() throws Exception {
        register(json("Carla", "carla@gmail.com", "plain-text-pw"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("plain-text-pw"))));
    }

    @Test
    void errorBodyStartsWithErrorsObject() throws Exception {
        register(json("", uniqueEmail(), VALID_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(startsWith("{\"errors\":")));
    }

}
