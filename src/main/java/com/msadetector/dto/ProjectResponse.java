package com.msadetector.dto;

import com.msadetector.enums.SourceType;

import java.time.LocalDateTime;
import java.util.List;

public record ProjectResponse(
        Long id,
        String name,
        SourceType sourceType,
        String sourceUrl,
        String branch,
        LocalDateTime createdAt,
        List<MicroserviceResponse> microservices,
        Integer analysisCount,
        Long latestJobId
) {}
