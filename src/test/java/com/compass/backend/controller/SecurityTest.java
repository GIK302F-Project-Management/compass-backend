package com.compass.backend.controller;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import javax.crypto.spec.SecretKeySpec;

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
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.compass.backend.model.User;
import com.compass.backend.model.UserRepository;
import com.compass.backend.service.PasswordHasher;
import com.compass.backend.service.TokenService;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * The real security filter chain: which endpoints are public, what a bad token gets, and CORS.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:sqlite:target/compass-test.db",
    "llm.enabled=false"
})
class SecurityTest {

    private static final String ANA = "ana@alumni.mondragon.edu";
    private static final String ORIGIN = "http://localhost:5173";
    private static final String PLAN_BODY = """
        {"startDate": "%s", "dailyAvailableMinutes": 60,
         "goals": [{"id": "g1", "title": "Learn Java", "deadline": "%s", "estimatedMinutes": 120}]}
        """;

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
        if (!users.existsByEmailIgnoreCase(ANA)) {
            users.save(new User(0, "Ana", ANA, PasswordHasher.hash("ana123")));
        }
    }

    private String loginToken() throws Exception {
        String body = mockMvc.perform(post("/user/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + ANA + "\", \"password\": \"ana123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private String tokenFor(JwtEncoder encoder, String subject, Instant issuedAt, Instant expiresAt, String issuer) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(issuer).subject(subject).issuedAt(issuedAt).expiresAt(expiresAt).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    }

    private int anaId() {
        return users.findByEmailIgnoreCase(ANA).orElseThrow().getId();
    }

    private String planBody() {
        return PLAN_BODY.formatted(LocalDate.now(), LocalDate.now().plusDays(10));
    }

    // ---------- public and protected endpoints ----------

    @Test
    void protectedEndpointsRejectRequestsWithoutToken() throws Exception {
        mockMvc.perform(get("/user/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/study-plans").contentType(MediaType.APPLICATION_JSON).content(planBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerAndLoginStayPublic() throws Exception {
        mockMvc.perform(post("/user/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"nobody@alumni.mondragon.edu\", \"password\": \"x\"}"))
                .andExpect(status().isUnauthorized()); // from the login rule, not from the filter
        mockMvc.perform(post("/user/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validTokenOpensProtectedEndpoints() throws Exception {
        String token = loginToken();

        mockMvc.perform(get("/user/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(ANA))
                .andExpect(jsonPath("$.password").doesNotExist());

        mockMvc.perform(post("/api/study-plans").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(planBody()))
                .andExpect(status().isOk());
    }

    // ---------- bad tokens ----------

    @Test
    void tamperedTokenIsRejected() throws Exception {
        String token = loginToken();
        String tampered = token.substring(0, token.length() - 3) + (token.endsWith("AAA") ? "BBB" : "AAA");

        mockMvc.perform(get("/user/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        Instant hourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        String token = tokenFor(jwtEncoder, String.valueOf(anaId()),
                hourAgo.minus(1, ChronoUnit.HOURS), hourAgo, TokenService.ISSUER);

        mockMvc.perform(get("/user/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        byte[] otherKey = "another-secret-another-secret-12".getBytes(StandardCharsets.UTF_8);
        JwtEncoder other = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(otherKey, "HmacSHA256")));
        String token = tokenFor(other, String.valueOf(anaId()),
                Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS), TokenService.ISSUER);

        mockMvc.perform(get("/user/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() throws Exception {
        String token = tokenFor(jwtEncoder, String.valueOf(anaId()),
                Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS), "someone-else");

        mockMvc.perform(get("/user/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenOfADeletedAccountIsRejected() throws Exception {
        String token = tokenFor(jwtEncoder, String.valueOf(Integer.MAX_VALUE),
                Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS), TokenService.ISSUER);

        mockMvc.perform(get("/user/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void eachLoginGetsItsOwnToken() throws Exception {
        String first = loginToken();
        Thread.sleep(1100); // tokens carry the issue time in seconds
        assertNotEquals(first, loginToken());
    }

    // ---------- CORS ----------

    @Test
    void preflightFromTheWebPageIsAllowed() throws Exception {
        mockMvc.perform(options("/api/study-plans")
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN));
    }

    @Test
    void preflightFromAnotherSiteIsRefused() throws Exception {
        mockMvc.perform(options("/api/study-plans")
                .header(HttpHeaders.ORIGIN, "http://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }

}
