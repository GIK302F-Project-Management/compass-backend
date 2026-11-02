package com.compass.backend.dto;

import java.time.LocalDate;

public record StudySession(
    String goalId,
    String title,
    LocalDate date,
    int durationMinutes
) {}