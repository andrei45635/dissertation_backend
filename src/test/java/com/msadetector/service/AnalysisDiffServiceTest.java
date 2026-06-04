package com.msadetector.service;

import com.msadetector.dto.AnalysisDiffResponse;
import com.msadetector.dto.HealthScoreBreakdownResponse;
import com.msadetector.dto.HealthScoreBreakdownResponse.Deduction;
import com.msadetector.dto.HealthScoreBreakdownResponse.ScoreCategory;
import com.msadetector.entity.AnalysisJob;
import com.msadetector.entity.AnalysisResult;
import com.msadetector.entity.DetectedAntiPattern;
import com.msadetector.entity.Microservice;
import com.msadetector.enums.AntiPatternType;
import com.msadetector.enums.Severity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisDiffServiceTest {

    @Mock private HealthScoreCalculator healthScoreCalculator;

    private AnalysisDiffService diffService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        diffService = new AnalysisDiffService(healthScoreCalculator, objectMapper);
    }

    @Test
    void buildDiff_healthImproved() {
        AnalysisResult prev = buildResult(3, 10, 5000, 2, 1, 1, 0, 1, 0.3, 5,
                List.of(buildAP(AntiPatternType.CYCLIC_DEPENDENCY, Severity.HIGH)));
        AnalysisResult curr = buildResult(1, 5, 6000, 1, 0, 0, 1, 0, 0.2, 4,
                List.of(buildAP(AntiPatternType.HARDCODED_ENDPOINTS, Severity.MEDIUM)));

        mockBreakdown(75, "C", 85, "B");

        AnalysisDiffResponse diff = diffService.buildDiff(curr, prev, 2);

        assertEquals(85, diff.currentHealthScore());
        assertEquals(75, diff.previousHealthScore());
        assertEquals(10, diff.healthScoreDelta());
        assertEquals("B", diff.currentGrade());
        assertTrue(diff.summary().contains("improved"));
    }

    @Test
    void buildDiff_healthDeclined() {
        AnalysisResult prev = buildResult(1, 5, 5000, 1, 0, 1, 0, 0, 0.1, 3, List.of());
        AnalysisResult curr = buildResult(3, 10, 5000, 2, 1, 1, 0, 1, 0.3, 5,
                List.of(buildAP(AntiPatternType.SHARED_DATABASE, Severity.CRITICAL)));

        mockBreakdown(85, "B", 60, "C");

        AnalysisDiffResponse diff = diffService.buildDiff(curr, prev, 2);

        assertTrue(diff.healthScoreDelta() < 0);
        assertTrue(diff.summary().contains("declined"));
    }

    @Test
    void buildDiff_resolvedAntiPatterns() {
        AnalysisResult prev = buildResult(1, 5, 5000, 1, 0, 1, 0, 0, 0.1, 3,
                List.of(buildAP(AntiPatternType.CYCLIC_DEPENDENCY, Severity.HIGH)));
        AnalysisResult curr = buildResult(0, 3, 5000, 0, 0, 0, 0, 0, 0.05, 2, List.of());

        mockBreakdown(80, "B", 95, "A");

        AnalysisDiffResponse diff = diffService.buildDiff(curr, prev, 2);

        assertEquals(1, diff.resolvedAntiPatterns().size());
        assertEquals(0, diff.newAntiPatterns().size());
        assertTrue(diff.summary().contains("resolved"));
    }

    @Test
    void buildDiff_newAntiPatterns() {
        AnalysisResult prev = buildResult(0, 3, 5000, 0, 0, 0, 0, 0, 0.05, 2, List.of());
        AnalysisResult curr = buildResult(2, 8, 5000, 1, 1, 0, 1, 0, 0.2, 3,
                List.of(
                        buildAP(AntiPatternType.SHARED_DATABASE, Severity.CRITICAL),
                        buildAP(AntiPatternType.CHATTY_SERVICE, Severity.MEDIUM)
                ));

        mockBreakdown(95, "A", 70, "C");

        AnalysisDiffResponse diff = diffService.buildDiff(curr, prev, 2);

        assertEquals(2, diff.newAntiPatterns().size());
        assertTrue(diff.summary().contains("new"));
    }

    @Test
    void buildDiff_unchangedHealth() {
        AnalysisResult prev = buildResult(1, 5, 5000, 1, 0, 1, 0, 0, 0.1, 3, List.of());
        AnalysisResult curr = buildResult(1, 5, 5000, 1, 0, 1, 0, 0, 0.1, 3, List.of());

        mockBreakdown(80, "B", 80, "B");

        AnalysisDiffResponse diff = diffService.buildDiff(curr, prev, 2);

        assertEquals(0, diff.healthScoreDelta());
        assertTrue(diff.summary().contains("unchanged"));
    }

    @Test
    void buildDiff_metricDeltas() {
        AnalysisResult prev = buildResult(2, 10, 5000, 2, 1, 1, 0, 0, 0.2, 4, List.of());
        AnalysisResult curr = buildResult(1, 8, 6000, 2, 0, 1, 1, 0, 0.15, 3, List.of());

        mockBreakdown(75, "C", 82, "B");

        AnalysisDiffResponse diff = diffService.buildDiff(curr, prev, 3);

        assertEquals(-2, diff.totalCodeSmells().delta());
        assertEquals(1000, diff.totalLinesOfCode().delta());
        assertEquals(3, diff.analysisNumber());
    }

    @Test
    void buildDiff_sameTypeDifferentService_isResolvedAndNew() {
        AnalysisResult prev = buildResult(1, 5, 5000, 1, 0, 0, 1, 0, 0.1, 3,
                List.of(buildAP(AntiPatternType.API_VERSIONING_ABSENCE, Severity.MEDIUM, "svc-a")));
        AnalysisResult curr = buildResult(1, 5, 5000, 1, 0, 0, 1, 0, 0.1, 3,
                List.of(buildAP(AntiPatternType.API_VERSIONING_ABSENCE, Severity.MEDIUM, "svc-b")));

        mockBreakdown(80, "B", 80, "B");

        AnalysisDiffResponse diff = diffService.buildDiff(curr, prev, 2);

        assertEquals(1, diff.resolvedAntiPatterns().size());
        assertEquals(1, diff.newAntiPatterns().size());
        assertEquals(0, diff.unchangedAntiPatterns().size());
        assertEquals(List.of("svc-a"), diff.resolvedAntiPatterns().getFirst().affectedServices());
        assertEquals(List.of("svc-b"), diff.newAntiPatterns().getFirst().affectedServices());
    }

    private void mockBreakdown(int prevScore, String prevGrade, int currScore, String currGrade) {
        List<ScoreCategory> cats = List.of(
                new ScoreCategory("Anti-Patterns", "", 30, 40, List.of()),
                new ScoreCategory("Code Quality", "", 18, 20, List.of()),
                new ScoreCategory("Architecture", "", 20, 25, List.of()),
                new ScoreCategory("Service Sizing", "", 12, 15, List.of())
        );

        when(healthScoreCalculator.calculate(any(AnalysisResult.class)))
                .thenReturn(new HealthScoreBreakdownResponse(currScore, currGrade, cats))
                .thenReturn(new HealthScoreBreakdownResponse(prevScore, prevGrade, cats));
    }

    private AnalysisResult buildResult(int antiPatterns, int codeSmells, int loc,
                                       int services, int critical, int high,
                                       int medium, int low, double coupling, int deps,
                                       List<DetectedAntiPattern> patterns) {
        AnalysisJob job = AnalysisJob.builder().build();
        job.setId(1L);
        job.setCreatedAt(LocalDateTime.now());

        AnalysisResult result = AnalysisResult.builder()
                .analysisJob(job)
                .totalAntiPatterns(antiPatterns)
                .totalCodeSmells(codeSmells)
                .totalLinesOfCode(loc)
                .servicesAnalyzed(services)
                .criticalIssues(critical)
                .highIssues(high)
                .mediumIssues(medium)
                .lowIssues(low)
                .couplingCoefficient(coupling)
                .totalDependencies(deps)
                .cycleCount(0)
                .detectedAntiPatterns(new ArrayList<>(patterns))
                .build();
        result.setCreatedAt(LocalDateTime.now());
        return result;
    }

    private DetectedAntiPattern buildAP(AntiPatternType type, Severity severity) {
        return DetectedAntiPattern.builder()
                .patternType(type)
                .severity(severity)
                .description("Test: " + type.getDisplayName())
                .affectedServicesJson("[\"svc-a\",\"svc-b\"]")
                .build();
    }

    private DetectedAntiPattern buildAP(AntiPatternType type, Severity severity, String serviceName) {
        Microservice service = Microservice.builder().name(serviceName).build();
        return DetectedAntiPattern.builder()
                .patternType(type)
                .severity(severity)
                .description("Test: " + type.getDisplayName())
                .affectedServicesJson("[\"" + serviceName + "\"]")
                .primaryService(service)
                .build();
    }
}

