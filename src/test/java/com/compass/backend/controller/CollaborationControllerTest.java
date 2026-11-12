package com.compass.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.compass.backend.config.SharedPlansMigration;
import com.compass.backend.model.ActivityInvitation;
import com.compass.backend.model.ActivityInvitationRepository;
import com.compass.backend.model.ActivityMemberRepository;
import com.compass.backend.model.ActivityRepository;
import com.compass.backend.model.TaskRepository;
import com.compass.backend.model.User;
import com.compass.backend.model.UserRepository;
import com.compass.backend.service.PasswordHasher;
import com.compass.backend.service.TokenService;
import com.jayway.jsonpath.JsonPath;

/**
 * Collaborative learning through the real security filter chain: every student has one atlas, and an
 * activity in it can be shared with other students through an invitation link. The members share one list of
 * tasks, each with an assignee, and students outside an activity never see it.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:sqlite:target/compass-test.db",
    "llm.enabled=false"
})
class CollaborationControllerTest {

    private static final String ANA = "ana@alumni.mondragon.edu";
    private static final String BEN = "ben@alumni.mondragon.edu";
    private static final String CARLA = "carla@alumni.mondragon.edu";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository users;

    @Autowired
    private ActivityRepository activities;

    @Autowired
    private ActivityMemberRepository members;

    @Autowired
    private ActivityInvitationRepository invitations;

    @Autowired
    private TaskRepository tasks;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SharedPlansMigration migration;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        ensureUserExists("Ana", ANA);
        ensureUserExists("Ben", BEN);
        ensureUserExists("Carla", CARLA);
        tasks.deleteAll(); // each test starts with empty atlases
        invitations.deleteAll();
        members.deleteAll();
        activities.deleteAll();
    }

    private void ensureUserExists(String name, String email) {
        if (!users.existsByEmailIgnoreCase(email)) {
            users.save(new User(0, name, email, PasswordHasher.hash("secret123")));
        }
    }

    private int userId(String email) {
        return users.findByEmailIgnoreCase(email).orElseThrow().getId();
    }

    /** A valid access token for the given user, issued the same way a login does. */
    private String bearer(String email) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(TokenService.ISSUER).subject(String.valueOf(userId(email)))
            .issuedAt(now).expiresAt(now.plus(1, ChronoUnit.HOURS)).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
        return "Bearer " + token;
    }

    // ---------- small request helpers ----------

    private ResultActions getAs(String email, String path) throws Exception {
        return mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(email)));
    }

    private ResultActions send(MockHttpServletRequestBuilder builder, String email, String json) throws Exception {
        return mockMvc.perform(builder.header(HttpHeaders.AUTHORIZATION, bearer(email))
            .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions removeAs(String email, String path) throws Exception {
        return mockMvc.perform(delete(path).header(HttpHeaders.AUTHORIZATION, bearer(email)));
    }

    private static int idOf(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }

    private static String tokenOf(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.token");
    }

    private int createActivity(String email, String title) throws Exception {
        return idOf(send(post("/activities"), email,
            "{\"title\": \"" + title + "\", \"subject\": \"Physics\", \"dueDate\": \"2026-12-01\"}")
            .andExpect(status().isCreated()));
    }

    private int createTask(String email, int activityId, String title) throws Exception {
        return idOf(send(post("/activities/" + activityId + "/tasks"), email, "{\"title\": \"" + title + "\"}")
            .andExpect(status().isCreated()));
    }

    private String invite(String email, int activityId) throws Exception {
        return tokenOf(send(post("/activities/" + activityId + "/invitations"), email, "")
            .andExpect(status().isCreated()));
    }

    /** Ana's activity with Ben already inside it (through a real invitation). */
    private int sharedActivity() throws Exception {
        int activityId = createActivity(ANA, "Group project");
        send(post("/invitations/" + invite(ANA, activityId) + "/accept"), BEN, "").andExpect(status().isOk());
        return activityId;
    }

    // ---------- login required ----------

    @Test
    void everyEndpointNeedsALogin() throws Exception {
        mockMvc.perform(get("/activities")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/activities").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/invitations/abc")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/activities/1/members")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/tasks")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/tasks/1")).andExpect(status().isUnauthorized());
    }

    // ---------- one atlas per student ----------

    @Test
    void aNewStudentHasAnEmptyAtlas() throws Exception {
        getAs(ANA, "/activities").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void createPutsTheActivityInTheStudentsAtlasAsOwner() throws Exception {
        send(post("/activities"), ANA, "{\"title\": \"  Lab report \", \"subject\": \"Physics\", \"dueDate\": \"2026-12-01\"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("Lab report"))
            .andExpect(jsonPath("$.role").value("OWNER"))
            .andExpect(jsonPath("$.ownerId").value(userId(ANA)))
            .andExpect(jsonPath("$.memberCount").value(1));

        getAs(ANA, "/activities").andExpect(jsonPath("$.length()").value(1));
        getAs(BEN, "/activities").andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void activityFieldsAreValidated() throws Exception {
        send(post("/activities"), ANA, "{\"title\": \" \", \"subject\": \"\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.title").value("Please give the activity a name."))
            .andExpect(jsonPath("$.errors.subject").value("Please enter the subject or area."))
            .andExpect(jsonPath("$.errors.dueDate").value("Please pick a due date."));
    }

    @Test
    void anActivityIsInvisibleToStudentsWhoAreNotMembers() throws Exception {
        int activityId = createActivity(ANA, "Lab report");
        int taskId = createTask(ANA, activityId, "Write intro");

        getAs(BEN, "/activities/" + activityId).andExpect(status().isNotFound());
        getAs(BEN, "/activities/" + activityId + "/tasks").andExpect(status().isNotFound());
        getAs(BEN, "/activities/" + activityId + "/members").andExpect(status().isNotFound());
        getAs(BEN, "/activities/" + activityId + "/progress").andExpect(status().isNotFound());
        send(put("/activities/" + activityId), BEN,
            "{\"title\": \"Sneaky\", \"subject\": \"X\", \"dueDate\": \"2026-12-01\"}").andExpect(status().isNotFound());
        send(post("/activities/" + activityId + "/tasks"), BEN, "{\"title\": \"x\"}").andExpect(status().isNotFound());
        send(patch("/tasks/" + taskId), BEN, "{\"done\": true}").andExpect(status().isNotFound());
        removeAs(BEN, "/tasks/" + taskId).andExpect(status().isNotFound());
        removeAs(BEN, "/activities/" + activityId).andExpect(status().isNotFound());
        getAs(BEN, "/tasks").andExpect(jsonPath("$.length()").value(0));
    }

    // ---------- sharing an activity ----------

    @Test
    void anInvitedStudentJoinsAndTheActivityShowsUpInTheirAtlas() throws Exception {
        createActivity(BEN, "Ben's own essay");
        int activityId = createActivity(ANA, "Group project");
        ResultActions invitation = send(post("/activities/" + activityId + "/invitations"), ANA, "")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.token").isString())
            .andExpect(jsonPath("$.expiresAt").exists());
        String token = tokenOf(invitation);

        getAs(BEN, "/invitations/" + token).andExpect(status().isOk())
            .andExpect(jsonPath("$.activityTitle").value("Group project"))
            .andExpect(jsonPath("$.subject").value("Physics"))
            .andExpect(jsonPath("$.dueDate").value("2026-12-01"))
            .andExpect(jsonPath("$.ownerName").value("Ana"))
            .andExpect(jsonPath("$.memberCount").value(1))
            .andExpect(jsonPath("$.alreadyMember").value(false));

        send(post("/invitations/" + token + "/accept"), BEN, "").andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(activityId))
            .andExpect(jsonPath("$.role").value("MEMBER"))
            .andExpect(jsonPath("$.memberCount").value(2));

        // One atlas: Ben's own activity and the shared one, side by side
        getAs(BEN, "/activities").andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].title").value("Ben's own essay"))
            .andExpect(jsonPath("$[0].memberCount").value(1))
            .andExpect(jsonPath("$[1].title").value("Group project"))
            .andExpect(jsonPath("$[1].memberCount").value(2));
        getAs(ANA, "/activities").andExpect(jsonPath("$.length()").value(1));
        getAs(BEN, "/invitations/" + token).andExpect(jsonPath("$.alreadyMember").value(true));
    }

    @Test
    void acceptingTwiceKeepsASingleMembershipAndTheLinkWorksForSeveralStudents() throws Exception {
        int activityId = createActivity(ANA, "Group project");
        String token = invite(ANA, activityId);

        send(post("/invitations/" + token + "/accept"), BEN, "").andExpect(status().isOk());
        send(post("/invitations/" + token + "/accept"), BEN, "").andExpect(jsonPath("$.memberCount").value(2));
        send(post("/invitations/" + token + "/accept"), CARLA, "").andExpect(jsonPath("$.memberCount").value(3));
    }

    @Test
    void anUnknownRevokedOrExpiredInvitationIsRefused() throws Exception {
        int activityId = createActivity(ANA, "Group project");
        getAs(BEN, "/invitations/does-not-exist").andExpect(status().isNotFound());
        send(post("/invitations/does-not-exist/accept"), BEN, "").andExpect(status().isNotFound());

        ResultActions created = send(post("/activities/" + activityId + "/invitations"), ANA, "");
        int invitationId = idOf(created);
        String token = tokenOf(created);
        removeAs(ANA, "/activities/" + activityId + "/invitations/" + invitationId).andExpect(status().isNoContent());
        send(post("/invitations/" + token + "/accept"), BEN, "").andExpect(status().isNotFound());
        getAs(ANA, "/activities/" + activityId + "/invitations").andExpect(jsonPath("$.length()").value(0));

        invitations.save(new ActivityInvitation(activityId, "old-token", userId(ANA),
            Instant.now().minus(1, ChronoUnit.DAYS)));
        send(post("/invitations/old-token/accept"), BEN, "").andExpect(status().isNotFound());
        getAs(ANA, "/activities/" + activityId + "/members").andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void onlyTheOwnerManagesInvitations() throws Exception {
        int activityId = sharedActivity();

        send(post("/activities/" + activityId + "/invitations"), BEN, "").andExpect(status().isForbidden());
        getAs(BEN, "/activities/" + activityId + "/invitations").andExpect(status().isForbidden());
        send(post("/activities/" + activityId + "/invitations"), CARLA, "").andExpect(status().isNotFound());
        getAs(ANA, "/activities/" + activityId + "/invitations").andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void membersAreListedByNameOnly() throws Exception {
        int activityId = sharedActivity();

        getAs(BEN, "/activities/" + activityId + "/members").andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].name").value("Ana"))
            .andExpect(jsonPath("$[0].owner").value(true))
            .andExpect(jsonPath("$[1].name").value("Ben"))
            .andExpect(jsonPath("$[1].you").value(true))
            .andExpect(jsonPath("$[0].email").doesNotExist());
    }

    @Test
    void everyMemberEditsAndCompletesButOnlyTheOwnerDeletes() throws Exception {
        int activityId = sharedActivity();

        send(put("/activities/" + activityId), BEN,
            "{\"title\": \"  Group project 2 \", \"subject\": \"Chemistry\", \"dueDate\": \"2026-12-15\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Group project 2"))
            .andExpect(jsonPath("$.role").value("MEMBER"));
        getAs(ANA, "/activities/" + activityId).andExpect(jsonPath("$.dueDate").value("2026-12-15"));

        removeAs(BEN, "/activities/" + activityId).andExpect(status().isForbidden());
        createTask(BEN, activityId, "Write intro");
        removeAs(ANA, "/activities/" + activityId).andExpect(status().isNoContent());

        getAs(BEN, "/activities/" + activityId).andExpect(status().isNotFound());
        getAs(BEN, "/activities").andExpect(jsonPath("$.length()").value(0));
        assertEquals(0, tasks.count());
        assertEquals(0, members.count());
    }

    @Test
    void aMemberCanLeaveButTheOwnerCannot() throws Exception {
        int activityId = sharedActivity();

        removeAs(ANA, "/activities/" + activityId + "/members/" + userId(ANA)).andExpect(status().isConflict());
        removeAs(BEN, "/activities/" + activityId + "/members/" + userId(BEN)).andExpect(status().isNoContent());

        getAs(BEN, "/activities/" + activityId).andExpect(status().isNotFound());
        getAs(BEN, "/activities").andExpect(jsonPath("$.length()").value(0));
        getAs(ANA, "/activities/" + activityId + "/members").andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void theOwnerCanRemoveAMemberAndMembersCannotRemoveOthers() throws Exception {
        int activityId = sharedActivity();

        removeAs(BEN, "/activities/" + activityId + "/members/" + userId(ANA)).andExpect(status().isForbidden());
        removeAs(ANA, "/activities/" + activityId + "/members/" + userId(CARLA)).andExpect(status().isNotFound());
        removeAs(ANA, "/activities/" + activityId + "/members/" + userId(BEN)).andExpect(status().isNoContent());

        getAs(BEN, "/activities/" + activityId).andExpect(status().isNotFound());
    }

    @Test
    void theTasksOfAStudentWhoLeavesStayWithoutAnAssignee() throws Exception {
        int activityId = sharedActivity();
        int bensTask = createTask(BEN, activityId, "Draw charts");
        createTask(ANA, activityId, "Write intro");

        removeAs(BEN, "/activities/" + activityId + "/members/" + userId(BEN)).andExpect(status().isNoContent());

        getAs(ANA, "/activities/" + activityId + "/tasks")
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value(bensTask))
            .andExpect(jsonPath("$[0].assigneeId").doesNotExist())
            .andExpect(jsonPath("$[1].assigneeId").value(userId(ANA)));
    }

    // ---------- tasks and assignees ----------

    @Test
    void aTaskStartsOpenAndAssignedToWhoeverAddsIt() throws Exception {
        int activityId = sharedActivity();

        send(post("/activities/" + activityId + "/tasks"), BEN,
            "{\"title\": \" Write intro \", \"details\": \"Two pages\", \"dueDate\": \"2026-11-30\", \"estimatedMinutes\": 90}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("Write intro"))
            .andExpect(jsonPath("$.details").value("Two pages"))
            .andExpect(jsonPath("$.dueDate").value("2026-11-30"))
            .andExpect(jsonPath("$.estimatedMinutes").value(90))
            .andExpect(jsonPath("$.assigneeId").value(userId(BEN)))
            .andExpect(jsonPath("$.done").value(false))
            .andExpect(jsonPath("$.activityId").value(activityId));

        send(post("/activities/" + activityId + "/tasks"), ANA, "{\"title\": \"Draw charts\"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.details").value(""))
            .andExpect(jsonPath("$.dueDate").doesNotExist())
            .andExpect(jsonPath("$.estimatedMinutes").doesNotExist());
    }

    @Test
    void taskFieldsAreValidated() throws Exception {
        int activityId = createActivity(ANA, "Lab report");

        send(post("/activities/" + activityId + "/tasks"), ANA, "{\"title\": \"\", \"estimatedMinutes\": 0}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.title").value("Please give the task a title."))
            .andExpect(jsonPath("$.errors.estimatedMinutes").exists());
        send(post("/activities/" + activityId + "/tasks"), ANA, "{\"title\": \"x\", \"estimatedMinutes\": 10081}")
            .andExpect(status().isBadRequest());
    }

    @Test
    void anyMemberCanHandATaskToAnotherMemberOrLeaveItUnassigned() throws Exception {
        int activityId = sharedActivity();
        int taskId = createTask(ANA, activityId, "Write intro");

        send(patch("/tasks/" + taskId), BEN, "{\"assigneeId\": " + userId(BEN) + "}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.assigneeId").value(userId(BEN)))
            .andExpect(jsonPath("$.title").value("Write intro"));

        send(patch("/tasks/" + taskId), ANA, "{\"assigneeId\": null}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.assigneeId").doesNotExist());

        send(patch("/tasks/" + taskId), ANA, "{\"assigneeId\": " + userId(CARLA) + "}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.assigneeId").value("That student is not in this activity."));
        send(patch("/tasks/" + taskId), ANA, "{\"assigneeId\": \"Ben\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.assigneeId").exists());
    }

    @Test
    void patchChangesOnlyTheFieldsSent() throws Exception {
        int activityId = createActivity(ANA, "Lab report");
        int taskId = idOf(send(post("/activities/" + activityId + "/tasks"), ANA,
            "{\"title\": \"Write intro\", \"details\": \"Two pages\", \"dueDate\": \"2026-11-30\", \"estimatedMinutes\": 90}"));

        send(patch("/tasks/" + taskId), ANA, "{\"title\": \"  Write the intro \"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Write the intro"))
            .andExpect(jsonPath("$.details").value("Two pages"))
            .andExpect(jsonPath("$.dueDate").value("2026-11-30"))
            .andExpect(jsonPath("$.estimatedMinutes").value(90))
            .andExpect(jsonPath("$.assigneeId").value(userId(ANA)));

        send(patch("/tasks/" + taskId), ANA, "{\"dueDate\": null, \"estimatedMinutes\": null}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Write the intro"))
            .andExpect(jsonPath("$.dueDate").doesNotExist())
            .andExpect(jsonPath("$.estimatedMinutes").doesNotExist());
    }

    @Test
    void patchRefusesWrongFieldsWithOneMessageEach() throws Exception {
        int activityId = createActivity(ANA, "Lab report");
        int taskId = createTask(ANA, activityId, "Write intro");

        send(patch("/tasks/" + taskId), ANA,
            "{\"title\": \"\", \"dueDate\": \"tomorrow\", \"estimatedMinutes\": 1.5, \"done\": \"yes\", \"activityId\": 7}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.title").value("Please give the task a title."))
            .andExpect(jsonPath("$.errors.dueDate").exists())
            .andExpect(jsonPath("$.errors.estimatedMinutes").exists())
            .andExpect(jsonPath("$.errors.done").exists())
            .andExpect(jsonPath("$.errors.activityId").value("This field cannot be changed."));
        send(patch("/tasks/" + taskId), ANA, "{}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.body").exists());
    }

    @Test
    void theStudentGetsTheTasksOfTheirWholeAtlasInOneRequest() throws Exception {
        int shared = sharedActivity();
        int bensOwn = createActivity(BEN, "Essay");
        int anasOwn = createActivity(ANA, "Reading");
        createTask(ANA, shared, "Write intro");
        createTask(BEN, bensOwn, "Pick a topic");
        createTask(ANA, anasOwn, "Chapter 1");

        // Ben sees the shared activity's tasks (whoever they are assigned to) and his own, not Ana's own
        getAs(BEN, "/tasks").andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].title").value("Write intro"))
            .andExpect(jsonPath("$[0].assigneeId").value(userId(ANA)))
            .andExpect(jsonPath("$[1].title").value("Pick a topic"));
    }

    @Test
    void aTaskCompletedByOneMemberIsDoneForAll() throws Exception {
        int activityId = sharedActivity();
        int taskId = createTask(ANA, activityId, "Write intro");

        send(patch("/tasks/" + taskId), BEN, "{\"done\": true}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.done").value(true))
            .andExpect(jsonPath("$.doneBy").value(userId(BEN)));
        getAs(ANA, "/activities/" + activityId + "/tasks").andExpect(jsonPath("$[0].done").value(true));

        removeAs(ANA, "/tasks/" + taskId).andExpect(status().isNoContent());
        getAs(BEN, "/activities/" + activityId + "/tasks").andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void completingAnActivityCompletesItsTasksAndCreditsTheMember() throws Exception {
        int activityId = sharedActivity();
        int doneTask = createTask(ANA, activityId, "Write intro");
        send(patch("/tasks/" + doneTask), ANA, "{\"done\": true}").andExpect(status().isOk());
        createTask(ANA, activityId, "Draw charts");

        send(put("/activities/" + activityId + "/done"), BEN, "{\"done\": true}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.done").value(true))
            .andExpect(jsonPath("$.doneBy").value(userId(BEN)));

        getAs(ANA, "/activities/" + activityId + "/tasks")
            .andExpect(jsonPath("$[0].doneBy").value(userId(ANA))) // Ana keeps her credit
            .andExpect(jsonPath("$[1].done").value(true))
            .andExpect(jsonPath("$[1].doneBy").value(userId(BEN)));
    }

    @Test
    void addingATaskOrReopeningOneReopensACompletedActivity() throws Exception {
        int activityId = createActivity(ANA, "Lab report");
        int taskId = createTask(ANA, activityId, "Write intro");
        send(put("/activities/" + activityId + "/done"), ANA, "{\"done\": true}").andExpect(status().isOk());

        send(patch("/tasks/" + taskId), ANA, "{\"done\": false}").andExpect(status().isOk());
        getAs(ANA, "/activities/" + activityId).andExpect(jsonPath("$.done").value(false));

        send(put("/activities/" + activityId + "/done"), ANA, "{\"done\": true}").andExpect(status().isOk());
        createTask(ANA, activityId, "Add references");
        getAs(ANA, "/activities/" + activityId).andExpect(jsonPath("$.done").value(false))
            .andExpect(jsonPath("$.doneBy").doesNotExist());
    }

    // ---------- progress ----------

    @Test
    void progressShowsHowTheTasksAreSharedOut() throws Exception {
        int activityId = sharedActivity();
        getAs(ANA, "/activities/" + activityId + "/progress").andExpect(status().isOk())
            .andExpect(jsonPath("$.percent").value(0))
            .andExpect(jsonPath("$.tasksTotal").value(0));

        int intro = createTask(ANA, activityId, "Write intro");
        createTask(ANA, activityId, "References");
        int charts = createTask(BEN, activityId, "Draw charts");
        int loose = createTask(BEN, activityId, "Print it");
        send(patch("/tasks/" + loose), BEN, "{\"assigneeId\": null}").andExpect(status().isOk());
        send(patch("/tasks/" + intro), ANA, "{\"done\": true}").andExpect(status().isOk());
        send(patch("/tasks/" + charts), BEN, "{\"done\": true}").andExpect(status().isOk());

        getAs(BEN, "/activities/" + activityId + "/progress").andExpect(status().isOk())
            .andExpect(jsonPath("$.tasksTotal").value(4))
            .andExpect(jsonPath("$.tasksDone").value(2))
            .andExpect(jsonPath("$.tasksUnassigned").value(1))
            .andExpect(jsonPath("$.percent").value(50))
            .andExpect(jsonPath("$.members.length()").value(2))
            .andExpect(jsonPath("$.members[0].name").value("Ana"))
            .andExpect(jsonPath("$.members[0].tasksAssigned").value(2))
            .andExpect(jsonPath("$.members[0].tasksDone").value(1))
            .andExpect(jsonPath("$.members[1].name").value("Ben"))
            .andExpect(jsonPath("$.members[1].tasksAssigned").value(1))
            .andExpect(jsonPath("$.members[1].tasksDone").value(1));
    }

    // ---------- moving the old shared plans ----------

    @Test
    void theOldSharedPlansBecomeSharedActivities() throws Exception {
        int ana = userId(ANA);
        int ben = userId(BEN);
        // Dates and instants exactly as Hibernate stores them in SQLite
        int sample = createActivity(ANA, "Sample");
        String at = jdbc.queryForObject("SELECT created_at FROM study_activities WHERE id = ?", String.class, sample);
        String due = jdbc.queryForObject("SELECT due_date FROM study_activities WHERE id = ?", String.class, sample);
        removeAs(ANA, "/activities/" + sample).andExpect(status().isNoContent());
        jdbc.execute("CREATE TABLE plans (id INTEGER PRIMARY KEY, name TEXT, owner_id INTEGER, created_at TEXT)");
        jdbc.execute("CREATE TABLE plan_members (id INTEGER PRIMARY KEY, plan_id INTEGER, user_id INTEGER, joined_at TEXT)");
        jdbc.execute("CREATE TABLE plan_invitations (id INTEGER PRIMARY KEY, plan_id INTEGER, token TEXT)");
        jdbc.execute("""
            CREATE TABLE activities (id INTEGER PRIMARY KEY, plan_id INTEGER NOT NULL, title TEXT, subject TEXT,
                due_date TEXT, done INTEGER, done_by INTEGER, created_by INTEGER, created_at TEXT)""");
        jdbc.execute("""
            CREATE TABLE tasks (id INTEGER PRIMARY KEY, activity_id INTEGER, plan_id INTEGER NOT NULL, title TEXT,
                details TEXT, due_date TEXT, estimated_minutes INTEGER, done INTEGER, done_by INTEGER,
                created_by INTEGER, created_at TEXT)""");
        jdbc.update("INSERT INTO plans VALUES (1, 'Exam group', ?, ?)", ana, at);
        jdbc.update("INSERT INTO plan_members VALUES (1, 1, ?, ?)", ana, at);
        jdbc.update("INSERT INTO plan_members VALUES (2, 1, ?, ?)", ben, at);
        jdbc.update("INSERT INTO activities VALUES (900, 1, 'Lab report', 'Physics', ?, 0, NULL, ?, ?)", due, ben, at);
        jdbc.update("INSERT INTO tasks VALUES (901, 900, 1, 'Draw charts', '', NULL, 60, 0, NULL, ?, ?)", ben, at);

        migration.run(null);

        getAs(BEN, "/activities").andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(900))
            .andExpect(jsonPath("$[0].title").value("Lab report"))
            .andExpect(jsonPath("$[0].ownerId").value(ana))
            .andExpect(jsonPath("$[0].memberCount").value(2));
        getAs(ANA, "/activities/900/tasks").andExpect(jsonPath("$[0].title").value("Draw charts"))
            .andExpect(jsonPath("$[0].estimatedMinutes").value(60))
            .andExpect(jsonPath("$[0].assigneeId").value(ben));
        Integer oldTables = jdbc.queryForObject("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' "
            + "AND name IN ('plans', 'plan_members', 'plan_invitations', 'activities', 'tasks')", Integer.class);
        assertEquals(0, oldTables);
        assertFalse(members.findByActivityIdAndUserId(900, ana).isEmpty());
    }

}
