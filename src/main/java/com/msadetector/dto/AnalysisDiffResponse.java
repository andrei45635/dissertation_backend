package com.msadetector.dto;

import com.msadetector.enums.AntiPatternType;
import com.msadetector.enums.Severity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Diff between the current analysis and the previous one for the same project.
 * Inspired by SonarQube's "New Code" concept.
 */
public record AnalysisDiffResponse(

        Long currentJobId,
        Long previousJobId,
        LocalDateTime currentAnalysisDate,
        LocalDateTime previousAnalysisDate,
        int analysisNumber,

        int currentHealthScore,
        int previousHealthScore,
        int healthScoreDelta,            // positive = improved
        String currentGrade,
        String previousGrade,

        MetricDelta totalAntiPatterns,
        MetricDelta totalCodeSmells,
        MetricDelta criticalIssues,
        MetricDelta highIssues,
        MetricDelta mediumIssues,
        MetricDelta lowIssues,
        MetricDelta totalLinesOfCode,
        MetricDelta servicesAnalyzed,
        MetricDelta cycleCount,
        MetricDelta totalDependencies,
        DoubleDelta couplingCoefficient,

        List<AntiPatternChange> resolvedAntiPatterns,
        List<AntiPatternChange> newAntiPatterns,
        List<AntiPatternChange> unchangedAntiPatterns,

        List<CategoryDelta> categoryDeltas,

        String summary   // human-readable one-liner, e.g. "Health improved from 52 (D) → 78 (C). 2 anti-patterns resolved, 1 new."

) {

    public record MetricDelta(int previous, int current, int delta) {}

    public record DoubleDelta(double previous, double current, double delta) {}

    public record AntiPatternChange(
            AntiPatternType patternType,
            Severity severity,
            String description,
            List<String> affectedServices
    ) {}

    public record CategoryDelta(
            String categoryName,
            int previousScore,
            int currentScore,
            int maxScore,
            int delta
    ) {}
}

