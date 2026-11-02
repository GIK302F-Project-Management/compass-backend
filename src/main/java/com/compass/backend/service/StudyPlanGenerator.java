package com.compass.backend.service;

import com.compass.backend.dto.StudyPlanRequest;
import com.compass.backend.dto.StudyPlanResponse;

public interface StudyPlanGenerator {

    StudyPlanResponse generate(StudyPlanRequest request);
}