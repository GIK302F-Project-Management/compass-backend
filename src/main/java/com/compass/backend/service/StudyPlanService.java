package com.compass.backend.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.compass.backend.dto.StudyGoalRequest;
import com.compass.backend.dto.StudyPlanRequest;
import com.compass.backend.dto.StudyPlanResponse;
import com.compass.backend.dto.StudySession;

@Service
public class StudyPlanService implements StudyPlanGenerator {

    //@SuppressWarnings("null")
    @Override
    public StudyPlanResponse generate(StudyPlanRequest request) {
        validateRules(request);

        List<StudyGoalRequest> orderedGoals =
            new ArrayList<>(request.goals());

        orderedGoals.sort(
            Comparator.comparing(StudyGoalRequest::deadline)
        );

        List<StudySession> sessions = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        Map<LocalDate, Integer> usedMinutes = new TreeMap<>();

        for (StudyGoalRequest goal : orderedGoals) {
            int remaining = goal.estimatedMinutes();
            LocalDate date = request.startDate();

            while (remaining > 0 && !date.isAfter(goal.deadline())) {
                int used = usedMinutes.getOrDefault(date, 0);

                int available =
                    request.dailyAvailableMinutes() - used;

                if (available > 0) {
                    int duration = Math.min(remaining, available);

                    sessions.add(new StudySession(
                        goal.id(),
                        goal.title().trim(),
                        date,
                        duration
                    ));

                    usedMinutes.put(date, used + duration);
                    remaining -= duration;
                }

                date = date.plusDays(1);
            }

            if (remaining > 0) {
                warnings.add(
                    "Goal " + goal.id() + ": "
                    + remaining
                    + " minutes could not be scheduled before the deadline."
                );
            }
        }

        sessions.sort(Comparator.comparing(StudySession::date));

        return new StudyPlanResponse(
            "rule-based",
            warnings.isEmpty() ? "ready" : "partial",
            List.copyOf(sessions),
            List.copyOf(warnings)
        );
    }

    private void validateRules(StudyPlanRequest request) {
        HashSet<String> ids = new HashSet<>();

        for (StudyGoalRequest goal : request.goals()) {
            if (!ids.add(goal.id())) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Goal IDs must be unique."
                );
            }

            long days = ChronoUnit.DAYS.between(
                request.startDate(),
                goal.deadline()
            );

            if (days < 0 || days > 365) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Deadlines must be between the start date "
                    + "and 365 days after it."
                );
            }
        }
    }
}