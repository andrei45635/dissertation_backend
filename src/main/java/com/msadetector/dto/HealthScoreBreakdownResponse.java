package com.msadetector.dto;

import java.util.List;

public record HealthScoreBreakdownResponse(
        int overallScore,
        String grade,
        List<ScoreCategory> categories
) {

    public record ScoreCategory(
            String name,
            String description,
            int score,
            int maxScore,
            List<Deduction> deductions
    ) {}

    public record Deduction(
            String reason,
            int count,
            int pointsLost
    ) {}
}

