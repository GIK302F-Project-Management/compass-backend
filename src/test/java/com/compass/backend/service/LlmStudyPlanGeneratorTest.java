package com.compass.backend.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.compass.backend.dto.StudyGoalRequest;
import com.compass.backend.dto.StudyPlanRequest;
import com.compass.backend.dto.StudyPlanResponse;
import com.compass.backend.dto.StudySession;

import jakarta.validation.Validation;
import tools.jackson.databind.json.JsonMapper;

/**
 * Checks how the model's answer is turned into a plan, without calling the AI provider.
 */
class LlmStudyPlanGeneratorTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 10);

    private final LlmStudyPlanGenerator generator = new LlmStudyPlanGenerator(
        JsonMapper.builder().build(),
        Validation.buildDefaultValidatorFactory().getValidator(),
        new StudyPlanService(),
        "https://example.invalid/api/v1",
        "test-key",
        "test-model"
    );

    // 60 minutes a day; "lab" (120 min) due in 2 days, "essay" (60 min) due in 4 days.
    private final StudyPlanRequest request = new StudyPlanRequest(
        START,
        60,
        List.of(
            new StudyGoalRequest("lab", "Lab report", START.plusDays(2), 120),
            new StudyGoalRequest("essay", "Essay", START.plusDays(4), 60)
        )
    );

    private static String session(String goalId, String title, LocalDate date, Object minutes) {
        String duration = minutes instanceof String ? "\"" + minutes + "\"" : String.valueOf(minutes);
        return """
            {"goalId": "%s", "title": "%s", "date": "%s", "durationMinutes": %s}
            """.formatted(goalId, title, date, duration);
    }

    private static String plan(String... sessions) {
        return "{\"sessions\": [" + String.join(",", sessions) + "]}";
    }

    private static Map<LocalDate, Integer> minutesPerDay(StudyPlanResponse response) {
        return response.sessions().stream().collect(Collectors.groupingBy(
            StudySession::date,
            Collectors.summingInt(StudySession::durationMinutes)
        ));
    }

    private static Map<String, Integer> minutesPerGoal(StudyPlanResponse response) {
        return response.sessions().stream().collect(Collectors.groupingBy(
            StudySession::goalId,
            Collectors.summingInt(StudySession::durationMinutes)
        ));
    }

    @Test
    void keepsAValidPlanAsItIs() {
        var response = generator.buildPlan(request, plan(
            session("lab", "Analyse the data", START, 60),
            session("lab", "Write the discussion", START.plusDays(1), 60),
            session("essay", "Outline the essay", START.plusDays(2), 60)
        ));

        assertEquals("llm", response.source());
        assertEquals("ready", response.status());
        assertEquals(3, response.sessions().size());
        assertEquals("Analyse the data", response.sessions().get(0).title());
        assertTrue(response.warnings().isEmpty());
    }

    @Test
    void shortensSessionsThatGoOverTheDailyLimit() {
        // 50 + 30 = 80 minutes on the first day, but only 60 are available.
        var response = generator.buildPlan(request, plan(
            session("lab", "Analyse the data", START, 50),
            session("essay", "Outline", START, 30)
        ));

        assertEquals(60, minutesPerDay(response).get(START));
        assertEquals(10, response.sessions().get(1).durationMinutes());
        assertEquals("partial", response.status());
    }

    @Test
    void shortensSessionsThatGoOverTheGoalEstimate() {
        // 60 + 60 + 30 = 150 minutes for a 120-minute goal.
        var response = generator.buildPlan(request, plan(
            session("lab", "Part 1", START, 60),
            session("lab", "Part 2", START.plusDays(1), 60),
            session("lab", "Part 3", START.plusDays(2), 30)
        ));

        assertEquals(120, minutesPerGoal(response).get("lab"));
        assertEquals(2, response.sessions().size());
    }

    @Test
    void dropsSessionsForUnknownGoalsOrOutsideTheDeadline() {
        var response = generator.buildPlan(request, plan(
            session("lab", "Before the start", START.minusDays(1), 30),
            session("lab", "After the deadline", START.plusDays(3), 30),
            session("ghost", "Not a goal", START, 30),
            session("essay", "Outline", START, 30)
        ));

        assertEquals(1, response.sessions().size());
        assertEquals("essay", response.sessions().get(0).goalId());
        assertTrue(response.warnings().contains("Goal lab: 120 minutes were not scheduled."));
        assertTrue(response.warnings().contains("Goal essay: 30 minutes were not scheduled."));
    }

    @Test
    void readsJsonWrappedInMarkdownAndLooseNumbers() {
        String answer = "Here is your plan:\n```json\n" + plan(
            session("lab", "Decimal minutes", START, 45.0),
            session("lab", "Text minutes", START.plusDays(1), "30")
        ) + "\n```";

        var response = generator.buildPlan(request, answer);

        assertEquals(2, response.sessions().size());
        assertEquals(45, response.sessions().get(0).durationMinutes());
        assertEquals(30, response.sessions().get(1).durationMinutes());
    }

    @Test
    void usesTheGoalTitleWhenTheModelLeavesItOut() {
        var response = generator.buildPlan(
            request,
            "{\"sessions\": [{\"goalId\": \"essay\", \"date\": \"" + START + "\", \"durationMinutes\": 30}]}"
        );

        assertEquals("Essay", response.sessions().get(0).title());
    }

    @Test
    void sortsSessionsByDate() {
        var response = generator.buildPlan(request, plan(
            session("essay", "Later", START.plusDays(3), 30),
            session("lab", "Earlier", START, 30)
        ));

        assertEquals(START, response.sessions().get(0).date());
        assertEquals(START.plusDays(3), response.sessions().get(1).date());
    }

    @Test
    void dropsSessionsOnRestDays() {
        // START is a Tuesday, so START + 4 is a Saturday and START + 5 a Sunday.
        var weekendOff = new StudyPlanRequest(
            START, 60, request.goals(), null, Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        );

        var response = generator.buildPlan(weekendOff, plan(
            session("essay", "On Friday", START.plusDays(3), 30),
            session("essay", "On Saturday", START.plusDays(4), 30)
        ));

        assertEquals(1, response.sessions().size());
        assertEquals(START.plusDays(3), response.sessions().get(0).date());
    }

    @Test
    void sendsRestDaysAsExactDatesAndThePreferences() {
        var withPreferences = new StudyPlanRequest(
            START, 60, request.goals(), "  Keep sessions short.  ", Set.of(DayOfWeek.SUNDAY)
        );

        var input = LlmStudyPlanGenerator.modelInput(withPreferences);

        // The last deadline is START + 4 (Saturday), so the first Sunday is outside the range.
        assertFalse(input.containsKey("unavailableDates"));
        assertEquals("Keep sessions short.", input.get("preferences"));

        var longer = new StudyPlanRequest(
            START, 60,
            List.of(new StudyGoalRequest("lab", "Lab report", START.plusDays(13), 60)),
            null, Set.of(DayOfWeek.SUNDAY)
        );

        assertEquals(
            List.of(START.plusDays(5), START.plusDays(12)),
            LlmStudyPlanGenerator.modelInput(longer).get("unavailableDates")
        );
        assertFalse(LlmStudyPlanGenerator.modelInput(longer).containsKey("preferences"));
    }

    @Test
    void rejectsAnAnswerThatIsNotJson() {
        var error = assertThrows(
            ResponseStatusException.class,
            () -> generator.buildPlan(request, "Sorry, I cannot help with that.")
        );

        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
    }

    @Test
    void rejectsAnAnswerWithoutAnyUsableSession() {
        var error = assertThrows(
            ResponseStatusException.class,
            () -> generator.buildPlan(request, plan(
                session("ghost", "Not a goal", START, 30),
                session("lab", "Too late", START.plusDays(10), 30)
            ))
        );

        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
    }

    @Test
    void rejectsAnEmptySessionList() {
        assertThrows(
            ResponseStatusException.class,
            () -> generator.buildPlan(request, plan())
        );
    }
}
