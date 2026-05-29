package com.msadetector.entity;

import com.msadetector.enums.AntiPatternType;
import com.msadetector.enums.Severity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnalysisResultTest {

    @Test
    void addAntiPattern_setsBackReference() {
        AnalysisResult result = AnalysisResult.builder().build();
        DetectedAntiPattern pattern = DetectedAntiPattern.builder()
                .patternType(AntiPatternType.CYCLIC_DEPENDENCY)
                .severity(Severity.HIGH)
                .build();

        result.addAntiPattern(pattern);

        assertEquals(1, result.getDetectedAntiPatterns().size());
        assertEquals(result, pattern.getAnalysisResult());
    }

    @Test
    void defaultValues() {
        AnalysisResult result = AnalysisResult.builder().build();

        assertEquals(100, result.getHealthScore());
        assertEquals(0, result.getServicesAnalyzed());
        assertEquals(0, result.getTotalAntiPatterns());
        assertEquals(0, result.getTotalCodeSmells());
        assertEquals(0, result.getCriticalIssues());
        assertEquals(0, result.getHighIssues());
        assertEquals(0, result.getMediumIssues());
        assertEquals(0, result.getLowIssues());
        assertEquals(0, result.getTotalLinesOfCode());
        assertEquals(0.0, result.getAverageServiceSize());
        assertEquals(0, result.getTotalDependencies());
        assertEquals(0, result.getCycleCount());
        assertEquals(0.0, result.getCouplingCoefficient());
        assertTrue(result.getDetectedAntiPatterns().isEmpty());
    }
}

