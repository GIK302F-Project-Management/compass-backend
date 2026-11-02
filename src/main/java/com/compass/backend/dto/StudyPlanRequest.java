package com.compass.backend.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StudyPlanRequest(
    @NotNull
    LocalDate startDate,

    @NotNull
    @Min(1)
    @Max(1440)
    Integer dailyAvailableMinutes,

    @NotEmpty
    @Size(max = 100)
    List<@NotNull @Valid StudyGoalRequest> goals
) {}