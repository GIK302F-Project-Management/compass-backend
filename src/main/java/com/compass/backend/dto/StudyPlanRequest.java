package com.compass.backend.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param preferences optional free text on how the user likes to study (e.g. "split big tasks
 *                    across several days"). Only the AI planner uses it, as a soft preference.
 * @param restDays    optional days of the week with no study at all (e.g. SUNDAY). Both planners
 *                    always respect them.
 */
public record StudyPlanRequest(
    @NotNull
    LocalDate startDate,

    @NotNull
    @Min(1)
    @Max(1440)
    Integer dailyAvailableMinutes,

    @NotEmpty
    @Size(max = 100)
    List<@NotNull @Valid StudyGoalRequest> goals,

    @Size(max = 500)
    String preferences,

    @Size(max = 6, message = "Keep at least one day of the week for studying.")
    Set<@NotNull DayOfWeek> restDays
) {

    /** A request without preferences or rest days. */
    public StudyPlanRequest(LocalDate startDate, Integer dailyAvailableMinutes, List<StudyGoalRequest> goals) {
        this(startDate, dailyAvailableMinutes, goals, null, null);
    }

    public boolean isRestDay(LocalDate date) {
        return restDays != null && restDays.contains(date.getDayOfWeek());
    }

    public boolean hasPreferences() {
        return preferences != null && !preferences.isBlank();
    }
}
