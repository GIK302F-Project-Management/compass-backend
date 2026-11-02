package com.compass.backend.controller;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.compass.backend.dto.StudyPlanRequest;
import com.compass.backend.dto.StudyPlanResponse;
import com.compass.backend.service.StudyPlanGenerator;

@RestController
@RequestMapping("/api/study-plans")
public class StudyPlanController {

    private final StudyPlanGenerator generator;

    public StudyPlanController(StudyPlanGenerator generator) {
        this.generator = generator;
    }

    @PostMapping
    public StudyPlanResponse create(
        @Valid @RequestBody StudyPlanRequest request
    ) {
        return generator.generate(request);
    }
}