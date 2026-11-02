package com.compass.backend.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StudyGoalRequest(
    @NotBlank
    @Size(max = 100)
    String id,

    @NotBlank
    @Size(max = 200)
    String title,

    @NotNull
    LocalDate deadline,

    @NotNull
    @Min(1)
    @Max(10080)
    Integer estimatedMinutes
) {}