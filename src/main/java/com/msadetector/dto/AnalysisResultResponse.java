package com.msadetector.dto;

import java.util.List;

public record AnalysisResultResponse(
        Long id,
        Long jobId,
        Integer healthScore,
        Integer servicesAnalyzed,
        Integer totalAntiPatterns,
        Integer totalCodeSmells,
        Integer criticalIssues,
        Integer highIssues,
        Integer mediumIssues,
        Integer lowIssues,
        Integer totalLinesOfCode,
        Double averageServiceSize,
        Integer cycleCount,
        List<AntiPatternResponse> antiPatterns,
        DependencyGraphResponse dependencyGraph
) {}
