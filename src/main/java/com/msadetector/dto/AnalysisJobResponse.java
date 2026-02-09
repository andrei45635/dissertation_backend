package com.msadetector.dto;

import com.msadetector.enums.JobStatus;

import java.time.LocalDateTime;

public record AnalysisJobResponse(
        Long id,
        Long projectId,
        JobStatus status,
        String currentPhase,
        String currentService,
        Integer servicesCompleted,
        Integer totalServices,
        Integer progressPercentage,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String errorMessage
) {}
