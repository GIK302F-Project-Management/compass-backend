package com.compass.backend.dto;

import java.util.List;

public record StudyPlanResponse(
    String source,
    String status,
    List<StudySession> sessions,
    List<String> warnings
) {}