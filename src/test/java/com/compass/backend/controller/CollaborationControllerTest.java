package com.compass.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import com.compass.backend.model.ActivityRepository;
import com.compass.backend.model.PlanInvitation;
import com.compass.backend.model.PlanInvitationRepository;
import com.compass.backend.model.PlanMemberRepository;
import com.compass.backend.model.PlanRepository;
import com.compass.backend.model.TaskRepository;
import com.compass.backend.model.User;
import com.compass.backend.model.UserRepository;
import com.compass.backend.service.PasswordHasher;
import com.compass.backend.service.TokenService;
import com.jayway.jsonpath.JsonPath;

/**
 * Collaborative learning through the real security filter chain: sharing a plan with an invitation
 * link, what members can do together in it, its progress, and that students outside a plan never see it.
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
    private PlanRepository plans;

    @Autowired
    private PlanMemberRepository members;

    @Autowired
    private PlanInvitationRepository invitations;

    @Autowired
    private ActivityRepository activities;

    @Autowired
    private TaskRepository tasks;

    @Autowired
    private JwtEncoder jwtEncoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        ensureUserExists("Ana", ANA);
        ensureUserExists("Ben", BEN);
        ensureUserExists("Carla", CARLA);
        tasks.deleteAll(); // each test starts with no plans
        activities.deleteAll();
        invitations.deleteAll();
        members.deleteAll();
        plans.deleteAll();
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

    private int createPlan(String email, String name) throws Exception {
        return idOf(send(post("/plans"), email, "{\"name\": \"" + name + "\"}").andExpect(status().isCreated()));
    }

    private int createActivity(String email, int planId, String title) throws Exception {
        return idOf(send(post("/plans/" + planId + "/activities"), email,
            "{\"title\": \"" + title + "\", \"subject\": \"Physics\", \"dueDate\": \"2026-12-01\"}")
            .andExpect(status().isCreated()));
    }

    private int createTask(String email, int activityId, String title) throws Exception {
        return idOf(send(post("/activities/" + activityId + "/tasks"), email, "{\"title\": \"" + title + "\"}")
            .andExpect(status().isCreated()));
    }

    /** Ana's plan with Ben already inside it (through a real invitation). */
    private int sharedPlan() throws Exception {
        int planId = createPlan(ANA, "Exam week");
        String token = tokenOf(send(post("/plans/" + planId + "/invitations"), ANA, "").andExpect(status().isCreated()));
        send(post("/invitations/" + token + "/accept"), BEN, "").andExpect(status().isOk());
        return planId;
    }

    // ---------- login required ----------

    @Test
    void everyEndpointNeedsALogin() throws Exception {
        mockMvc.perform(get("/plans")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/plans").contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"x\"}"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/invitations/abc")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/plans/1/activities")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/activities/1/tasks")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/tasks/1")).andExpect(status().isUnauthorized());
    }

    // ---------- plans ----------

    @Test
    void aStudentWithoutPlansGetsOnePersonalPlanOnlyOnce() throws Exception {
        getAs(ANA, "/plans").andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("My plan"))
            .andExpect(jsonPath("$[0].role").value("OWNER"))
            .andExpect(jsonPath("$[0].memberCount").value(1));

        getAs(ANA, "/plans").andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void createSavesThePlanWithTheStudentAsOwner() throws Exception {
        send(post("/plans"), ANA, "{\"name\": \"  Study group  \"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Study group"))
            .andExpect(jsonPath("$.role").value("OWNER"));
    }

    @Test
    void createRequiresAName() throws Exception {
        send(post("/plans"), ANA, "{\"name\": \"   \"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.name").value("Please give the plan a name."));
    }

    @Test
    void aPlanIsInvisibleToStudentsWhoAreNotMembers() throws Exception {
        int planId = createPlan(ANA, "Private");
        int activityId = createActivity(ANA, planId, "Lab report");
        int taskId = createTask(ANA, activityId, "Write intro");

        getAs(BEN, "/plans/" + planId).andExpect(status().isNotFound());
        getAs(BEN, "/plans/" + planId + "/progress").andExpect(status().isNotFound());
        getAs(BEN, "/plans/" + planId + "/activities").andExpect(status().isNotFound());
        getAs(BEN, "/plans/" + planId + "/tasks").andExpect(status().isNotFound());
        getAs(BEN, "/activities/" + activityId).andExpect(status().isNotFound());
        getAs(BEN, "/activities/" + activityId + "/tasks").andExpect(status().isNotFound());
        send(post("/plans/" + planId + "/activities"), BEN,
            "{\"title\": \"Sneaky\", \"subject\": \"X\", \"dueDate\": \"2026-12-01\"}").andExpect(status().isNotFound());
        send(patch("/tasks/" + taskId), BEN, "{\"done\": true}").andExpect(status().isNotFound());
        removeAs(BEN, "/tasks/" + taskId).andExpect(status().isNotFound());
        removeAs(BEN, "/activities/" + activityId).andExpect(status().isNotFound());
        removeAs(BEN, "/plans/" + planId).andExpect(status().isNotFound());
        getAs(BEN, "/plans").andExpect(jsonPath("$.length()").value(1)); // only Ben's own personal plan
    }

    @Test
    void theOwnerCanRenameAndMembersCannot() throws Exception {
        int planId = sharedPlan();

        send(put("/plans/" + planId), ANA, "{\"name\": \"Finals\"}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Finals"));
        send(put("/plans/" + planId), BEN, "{\"name\": \"Mine now\"}").andExpect(status().isForbidden());
        removeAs(BEN, "/plans/" + planId).andExpect(status().isForbidden());
    }

    @Test
    void planDetailsListMembersByNameOnly() throws Exception {
        int planId = sharedPlan();

        getAs(BEN, "/plans/" + planId).andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("MEMBER"))
            .andExpect(jsonPath("$.members.length()").value(2))
            .andExpect(jsonPath("$.members[0].name").value("Ana"))
            .andExpect(jsonPath("$.members[0].owner").value(true))
            .andExpect(jsonPath("$.members[1].name").value("Ben"))
            .andExpect(jsonPath("$.members[1].you").value(true))
            .andExpect(jsonPath("$.members[0].email").doesNotExist());
    }

    @Test
    void deletingAPlanRemovesEverythingInIt() throws Exception {
        int planId = sharedPlan();
        int activityId = createActivity(ANA, planId, "Lab report");
        createTask(ANA, activityId, "Write intro");

        removeAs(ANA, "/plans/" + planId).andExpect(status().isNoContent());

        getAs(ANA, "/plans/" + planId).andExpect(status().isNotFound());
        getAs(BEN, "/activities/" + activityId).andExpect(status().isNotFound());
        assertEquals(0, tasks.count());
        assertEquals(0, activities.count());
    }

    // ---------- invitations ----------

    @Test
    void anInvitedStudentJoinsAndSeesTheSharedPlan() throws Exception {
        int planId = createPlan(ANA, "Exam week");
        createActivity(ANA, planId, "Lab report");
        ResultActions invitation = send(post("/plans/" + planId + "/invitations"), ANA, "")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.token").isString())
            .andExpect(jsonPath("$.expiresAt").exists());
        String token = tokenOf(invitation);

        getAs(BEN, "/invitations/" + token).andExpect(status().isOk())
            .andExpect(jsonPath("$.planName").value("Exam week"))
            .andExpect(jsonPath("$.ownerName").value("Ana"))
            .andExpect(jsonPath("$.memberCount").value(1))
            .andExpect(jsonPath("$.alreadyMember").value(false));

        send(post("/invitations/" + token + "/accept"), BEN, "").andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(planId))
            .andExpect(jsonPath("$.role").value("MEMBER"))
            .andExpect(jsonPath("$.memberCount").value(2));

        getAs(BEN, "/plans/" + planId + "/activities").andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].title").value("Lab report"));
        getAs(BEN, "/invitations/" + token).andExpect(jsonPath("$.alreadyMember").value(true));
    }

    @Test
    void acceptingTwiceKeepsASingleMembership() throws Exception {
        int planId = createPlan(ANA, "Exam week");
        String token = tokenOf(send(post("/plans/" + planId + "/invitations"), ANA, ""));

        send(post("/invitations/" + token + "/accept"), BEN, "").andExpect(status().isOk());
        send(post("/invitations/" + token + "/accept"), BEN, "").andExpect(status().isOk())
            .andExpect(jsonPath("$.memberCount").value(2));
    }

    @Test
    void theLinkWorksForSeveralStudents() throws Exception {
        int planId = createPlan(ANA, "Exam week");
        String token = tokenOf(send(post("/plans/" + planId + "/invitations"), ANA, ""));

        send(post("/invitations/" + token + "/accept"), BEN, "").andExpect(status().isOk());
        send(post("/invitations/" + token + "/accept"), CARLA, "").andExpect(status().isOk())
            .andExpect(jsonPath("$.memberCount").value(3));
    }

    @Test
    void anUnknownRevokedOrExpiredInvitationIsRefused() throws Exception {
        int planId = createPlan(ANA, "Exam week");
        getAs(BEN, "/invitations/does-not-exist").andExpect(status().isNotFound());
        send(post("/invitations/does-not-exist/accept"), BEN, "").andExpect(status().isNotFound());

        ResultActions created = send(post("/plans/" + planId + "/invitations"), ANA, "");
        int invitationId = idOf(created);
        String token = tokenOf(created);
        removeAs(ANA, "/plans/" + planId + "/invitations/" + invitationId).andExpect(status().isNoContent());
        send(post("/invitations/" + token + "/accept"), BEN, "").andExpect(status().isNotFound());

        invitations.save(new PlanInvitation(planId, "old-token", userId(ANA), Instant.now().minus(1, ChronoUnit.DAYS)));
        send(post("/invitations/old-token/accept"), BEN, "").andExpect(status().isNotFound());
        getAs(ANA, "/plans/" + planId).andExpect(jsonPath("$.members.length()").value(1));
    }

    @Test
    void onlyTheOwnerManagesInvitations() throws Exception {
        int planId = sharedPlan();

        send(post("/plans/" + planId + "/invitations"), BEN, "").andExpect(status().isForbidden());
        getAs(BEN, "/plans/" + planId + "/invitations").andExpect(status().isForbidden());
        send(post("/plans/" + planId + "/invitations"), CARLA, "").andExpect(status().isNotFound());

        getAs(ANA, "/plans/" + planId + "/invitations").andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void revokedInvitationsDisappearFromTheList() throws Exception {
        int planId = createPlan(ANA, "Exam week");
        int invitationId = idOf(send(post("/plans/" + planId + "/invitations"), ANA, ""));

        removeAs(ANA, "/plans/" + planId + "/invitations/" + invitationId).andExpect(status().isNoContent());

        getAs(ANA, "/plans/" + planId + "/invitations").andExpect(jsonPath("$.length()").value(0));
    }

    // ---------- members ----------

    @Test
    void aMemberCanLeaveButTheOwnerCannot() throws Exception {
        int planId = sharedPlan();

        removeAs(ANA, "/plans/" + planId + "/members/" + userId(ANA)).andExpect(status().isConflict());
        removeAs(BEN, "/plans/" + planId + "/members/" + userId(BEN)).andExpect(status().isNoContent());

        getAs(BEN, "/plans/" + planId).andExpect(status().isNotFound());
        getAs(ANA, "/plans/" + planId).andExpect(jsonPath("$.members.length()").value(1));
    }

    @Test
    void theOwnerCanRemoveAMemberAndMembersCannotRemoveOthers() throws Exception {
        int planId = sharedPlan();

        removeAs(BEN, "/plans/" + planId + "/members/" + userId(ANA)).andExpect(status().isForbidden());
        removeAs(ANA, "/plans/" + planId + "/members/" + userId(CARLA)).andExpect(status().isNotFound());
        removeAs(ANA, "/plans/" + planId + "/members/" + userId(BEN)).andExpect(status().isNoContent());

        getAs(BEN, "/plans/" + planId).andExpect(status().isNotFound());
    }

    // ---------- activities ----------

    @Test
    void membersAddEditAndDeleteActivitiesTogether() throws Exception {
        int planId = sharedPlan();

        int activityId = createActivity(BEN, planId, "Lab report");
        getAs(ANA, "/plans/" + planId + "/activities").andExpect(jsonPath("$[0].title").value("Lab report"));

        send(put("/activities/" + activityId), ANA,
            "{\"title\": \"  Lab report 2 \", \"subject\": \"Chemistry\", \"dueDate\": \"2026-12-15\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Lab report 2"))
            .andExpect(jsonPath("$.subject").value("Chemistry"))
            .andExpect(jsonPath("$.dueDate").value("2026-12-15"));
        getAs(BEN, "/activities/" + activityId).andExpect(jsonPath("$.title").value("Lab report 2"));

        removeAs(BEN, "/activities/" + activityId).andExpect(status().isNoContent());
        getAs(ANA, "/activities/" + activityId).andExpect(status().isNotFound());
    }

    @Test
    void activityFieldsAreValidated() throws Exception {
        int planId = createPlan(ANA, "Exam week");

        send(post("/plans/" + planId + "/activities"), ANA, "{\"title\": \" \", \"subject\": \"\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.title").value("Please give the activity a name."))
            .andExpect(jsonPath("$.errors.subject").value("Please enter the subject or area."))
            .andExpect(jsonPath("$.errors.dueDate").value("Please pick a due date."));
    }

    @Test
    void completingAnActivityCompletesItsTasksAndCreditsTheMember() throws Exception {
        int planId = sharedPlan();
        int activityId = createActivity(ANA, planId, "Lab report");
        int doneTask = createTask(ANA, activityId, "Write intro");
        send(patch("/tasks/" + doneTask), ANA, "{\"done\": true}").andExpect(status().isOk());
        createTask(ANA, activityId, "Draw charts");

        send(put("/activities/" + activityId + "/done"), BEN, "{\"done\": true}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.done").value(true))
            .andExpect(jsonPath("$.doneBy").value(userId(BEN)));

        getAs(ANA, "/activities/" + activityId + "/tasks")
            .andExpect(jsonPath("$[0].done").value(true))
            .andExpect(jsonPath("$[0].doneBy").value(userId(ANA))) // Ana keeps her credit
            .andExpect(jsonPath("$[1].done").value(true))
            .andExpect(jsonPath("$[1].doneBy").value(userId(BEN)));
    }

    @Test
    void addingATaskOrReopeningOneReopensACompletedActivity() throws Exception {
        int planId = createPlan(ANA, "Exam week");
        int activityId = createActivity(ANA, planId, "Lab report");
        int taskId = createTask(ANA, activityId, "Write intro");
        send(put("/activities/" + activityId + "/done"), ANA, "{\"done\": true}").andExpect(status().isOk());

        send(patch("/tasks/" + taskId), ANA, "{\"done\": false}").andExpect(status().isOk());
        getAs(ANA, "/activities/" + activityId).andExpect(jsonPath("$.done").value(false));

        send(put("/activities/" + activityId + "/done"), ANA, "{\"done\": true}").andExpect(status().isOk());
        createTask(ANA, activityId, "Add references");
        getAs(ANA, "/activities/" + activityId).andExpect(jsonPath("$.done").value(false))
            .andExpect(jsonPath("$.doneBy").doesNotExist());
    }

    // ---------- tasks ----------

    @Test
    void aTaskStartsOpenWithOptionalDeadlineAndEstimate() throws Exception {
        int planId = createPlan(ANA, "Exam week");
        int activityId = createActivity(ANA, planId, "Lab report");

        send(post("/activities/" + activityId + "/tasks"), ANA,
            "{\"title\": \" Write intro \", \"details\": \"Two pages\", \"dueDate\": \"2026-11-30\", \"estimatedMinutes\": 90}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("Write intro"))
            .andExpect(jsonPath("$.details").value("Two pages"))
            .andExpect(jsonPath("$.dueDate").value("2026-11-30"))
            .andExpect(jsonPath("$.estimatedMinutes").value(90))
            .andExpect(jsonPath("$.done").value(false))
            .andExpect(jsonPath("$.planId").value(planId));

        send(post("/activities/" + activityId + "/tasks"), ANA, "{\"title\": \"Draw charts\"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.details").value(""))
            .andExpect(jsonPath("$.dueDate").doesNotExist())
            .andExpect(jsonPath("$.estimatedMinutes").doesNotExist());
    }

    @Test
    void taskFieldsAreValidated() throws Exception {
        int planId = createPlan(ANA, "Exam week");
        int activityId = createActivity(ANA, planId, "Lab report");

        send(post("/activities/" + activityId + "/tasks"), ANA, "{\"title\": \"\", \"estimatedMinutes\": 0}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.title").value("Please give the task a title."))
            .andExpect(jsonPath("$.errors.estimatedMinutes").exists());
        send(post("/activities/" + activityId + "/tasks"), ANA, "{\"title\": \"x\", \"estimatedMinutes\": 10081}")
            .andExpect(status().isBadRequest());
    }

    @Test
    void patchChangesOnlyTheFieldsSent() throws Exception {
        int planId = createPlan(ANA, "Exam week");
        int activityId = createActivity(ANA, planId, "Lab report");
        int taskId = idOf(send(post("/activities/" + activityId + "/tasks"), ANA,
            "{\"title\": \"Write intro\", \"details\": \"Two pages\", \"dueDate\": \"2026-11-30\", \"estimatedMinutes\": 90}"));

        send(patch("/tasks/" + taskId), ANA, "{\"title\": \"  Write the intro \"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Write the intro"))
            .andExpect(jsonPath("$.details").value("Two pages"))
            .andExpect(jsonPath("$.dueDate").value("2026-11-30"))
            .andExpect(jsonPath("$.estimatedMinutes").value(90));

        send(patch("/tasks/" + taskId), ANA, "{\"dueDate\": null, \"estimatedMinutes\": null}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Write the intro"))
            .andExpect(jsonPath("$.dueDate").doesNotExist())
            .andExpect(jsonPath("$.estimatedMinutes").doesNotExist());
    }

    @Test
    void patchRefusesWrongFieldsWithOneMessageEach() throws Exception {
        int planId = createPlan(ANA, "Exam week");
        int activityId = createActivity(ANA, planId, "Lab report");
        int taskId = createTask(ANA, activityId, "Write intro");

        send(patch("/tasks/" + taskId), ANA,
            "{\"title\": \"\", \"dueDate\": \"tomorrow\", \"estimatedMinutes\": 1.5, \"done\": \"yes\", \"planId\": 7}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.title").value("Please give the task a title."))
            .andExpect(jsonPath("$.errors.dueDate").exists())
            .andExpect(jsonPath("$.errors.estimatedMinutes").exists())
            .andExpect(jsonPath("$.errors.done").exists())
            .andExpect(jsonPath("$.errors.planId").value("This field cannot be changed."));
        send(patch("/tasks/" + taskId), ANA, "{}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.body").exists());
    }

    @Test
    void everyMemberSeesTheTasksOfThePlanInOneRequest() throws Exception {
        int planId = sharedPlan();
        int first = createActivity(ANA, planId, "Lab report");
        int second = createActivity(BEN, planId, "Essay");
        createTask(ANA, first, "Write intro");
        createTask(BEN, second, "Pick a topic");

        getAs(BEN, "/plans/" + planId + "/tasks").andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].title").value("Write intro"))
            .andExpect(jsonPath("$[1].title").value("Pick a topic"));
    }

    @Test
    void aTaskCompletedByOneMemberIsDoneForAll() throws Exception {
        int planId = sharedPlan();
        int activityId = createActivity(ANA, planId, "Lab report");
        int taskId = createTask(ANA, activityId, "Write intro");

        send(patch("/tasks/" + taskId), BEN, "{\"done\": true}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.done").value(true))
            .andExpect(jsonPath("$.doneBy").value(userId(BEN)));

        getAs(ANA, "/activities/" + activityId + "/tasks").andExpect(jsonPath("$[0].done").value(true));

        removeAs(ANA, "/tasks/" + taskId).andExpect(status().isNoContent());
        getAs(BEN, "/activities/" + activityId + "/tasks").andExpect(jsonPath("$.length()").value(0));
    }

    // ---------- progress ----------

    @Test
    void anEmptyPlanHasNoProgressYet() throws Exception {
        int planId = createPlan(ANA, "Exam week");

        getAs(ANA, "/plans/" + planId + "/progress").andExpect(status().isOk())
            .andExpect(jsonPath("$.percent").value(0))
            .andExpect(jsonPath("$.activitiesTotal").value(0))
            .andExpect(jsonPath("$.tasksTotal").value(0));
    }

    @Test
    void progressCountsTasksAndActivitiesWithoutTasksAndCreditsEachMember() throws Exception {
        int planId = sharedPlan();
        int lab = createActivity(ANA, planId, "Lab report");
        int essay = createActivity(ANA, planId, "Essay");
        int reading = createActivity(ANA, planId, "Reading"); // no tasks: counts as one step
        int intro = createTask(ANA, lab, "Write intro");
        int charts = createTask(ANA, lab, "Draw charts");
        createTask(ANA, essay, "Pick a topic");
        send(patch("/tasks/" + intro), ANA, "{\"done\": true}").andExpect(status().isOk());
        send(patch("/tasks/" + charts), BEN, "{\"done\": true}").andExpect(status().isOk());
        send(put("/activities/" + lab + "/done"), BEN, "{\"done\": true}").andExpect(status().isOk());
        send(put("/activities/" + reading + "/done"), ANA, "{\"done\": true}").andExpect(status().isOk());

        // steps: 3 tasks + 1 activity without tasks = 4; done: 2 tasks + the reading = 3 -> 75%
        getAs(BEN, "/plans/" + planId + "/progress").andExpect(status().isOk())
            .andExpect(jsonPath("$.activitiesTotal").value(3))
            .andExpect(jsonPath("$.activitiesDone").value(2))
            .andExpect(jsonPath("$.tasksTotal").value(3))
            .andExpect(jsonPath("$.tasksDone").value(2))
            .andExpect(jsonPath("$.percent").value(75))
            .andExpect(jsonPath("$.members.length()").value(2))
            .andExpect(jsonPath("$.members[0].name").value("Ana"))
            .andExpect(jsonPath("$.members[0].tasksDone").value(1))
            .andExpect(jsonPath("$.members[0].activitiesDone").value(1))
            .andExpect(jsonPath("$.members[1].name").value("Ben"))
            .andExpect(jsonPath("$.members[1].tasksDone").value(1))
            .andExpect(jsonPath("$.members[1].activitiesDone").value(1));
    }

}
