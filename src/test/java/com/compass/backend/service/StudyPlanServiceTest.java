package com.compass.backend.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.compass.backend.dto.StudyGoalRequest;
import com.compass.backend.dto.StudyPlanRequest;
import com.compass.backend.dto.StudySession;

class StudyPlanServiceTest {

    private final StudyPlanService service = new StudyPlanService();

    @Test
    void sharesDailyCapacityAndPrioritizesEarlierDeadline() {
        LocalDate start = LocalDate.of(2026, 11, 2);

        var request = new StudyPlanRequest(
            start,
            60,
            List.of(
                new StudyGoalRequest(
                    "later", "Exercises", start.plusDays(2), 60
                ),
                new StudyGoalRequest(
                    "earlier", "Genetics", start.plusDays(1), 90
                )
            )
        );

        var result = service.generate(request);

        assertEquals("ready", result.status());
        assertTrue(result.warnings().isEmpty());
        assertEquals("earlier", result.sessions().getFirst().goalId());

        assertEquals(
            150,
            result.sessions().stream()
                .mapToInt(StudySession::durationMinutes)
                .sum()
        );

        var dailyTotals = result.sessions().stream()
            .collect(Collectors.groupingBy(
                StudySession::date,
                Collectors.summingInt(
                    StudySession::durationMinutes
                )
            ));

        assertTrue(
            dailyTotals.values().stream()
                .allMatch(minutes -> minutes <= 60)
        );

        assertTrue(
            result.sessions().stream()
                .filter(session -> session.goalId().equals("earlier"))
                .allMatch(session ->
                    !session.date().isAfter(start.plusDays(1))
                )
        );
    }

    @Test
    void returnsPartialPlanWhenTimeIsInsufficient() {
        LocalDate start = LocalDate.of(2026, 11, 2);

        var request = new StudyPlanRequest(
            start,
            60,
            List.of(
                new StudyGoalRequest(
                    "goal-1", "Genetics", start, 120
                )
            )
        );

        var result = service.generate(request);

        assertEquals("partial", result.status());
        assertEquals(1, result.sessions().size());
        assertEquals(60, result.sessions().getFirst().durationMinutes());
        assertFalse(result.warnings().isEmpty());
    }

    @Test
    void skipsRestDays() {
        LocalDate saturday = LocalDate.of(2026, 11, 14);

        var request = new StudyPlanRequest(
            saturday,
            60,
            List.of(new StudyGoalRequest("goal-1", "Genetics", saturday.plusDays(2), 120)),
            null,
            java.util.Set.of(java.time.DayOfWeek.SUNDAY)
        );

        var result = service.generate(request);

        assertEquals("ready", result.status());
        assertEquals(
            List.of(saturday, saturday.plusDays(2)),
            result.sessions().stream().map(StudySession::date).toList()
        );
    }

    @Test
    void rejectsDeadlineBeforeStartDate() {
        LocalDate start = LocalDate.of(2026, 11, 2);

        var request = new StudyPlanRequest(
            start,
            60,
            List.of(
                new StudyGoalRequest(
                    "goal-1", "Genetics", start.minusDays(1), 60
                )
            )
        );

        var error = assertThrows(
            ResponseStatusException.class,
            () -> service.generate(request)
        );

        assertEquals(400, error.getStatusCode().value());
    }
}