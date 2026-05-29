package com.msadetector.service;

import com.msadetector.dto.HealthScoreBreakdownResponse;
import com.msadetector.dto.HealthScoreBreakdownResponse.Deduction;
import com.msadetector.dto.HealthScoreBreakdownResponse.ScoreCategory;
import com.msadetector.entity.AnalysisResult;
import com.msadetector.entity.DetectedAntiPattern;
import com.msadetector.enums.AntiPatternType;
import com.msadetector.enums.Severity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Computes a detailed health-score breakdown from an {@link AnalysisResult}.
 * <p>
 * The score budget (100 points) is split across four categories:
 * <ul>
 *   <li><b>Anti-Patterns</b> (40 pts) — penalty per detected anti-pattern, weighted by severity</li>
 *   <li><b>Code Quality</b> (20 pts) — penalty based on code smell density</li>
 *   <li><b>Architecture</b> (25 pts) — coupling coefficient and cyclic dependency impact</li>
 *   <li><b>Service Sizing</b> (15 pts) — nano / god service penalties</li>
 * </ul>
 */
@Service
public class HealthScoreCalculator {

    private static final int ANTI_PATTERN_MAX   = 40;
    private static final int CODE_QUALITY_MAX   = 20;
    private static final int ARCHITECTURE_MAX   = 25;
    private static final int SERVICE_SIZING_MAX = 15;

    private static final Map<Severity, Integer> SEVERITY_PENALTY = Map.of(
            Severity.CRITICAL, 8,
            Severity.HIGH,     5,
            Severity.MEDIUM,   3,
            Severity.LOW,      1
    );

    /**
     * Smell density (smells per 1000 LOC) at which the Code Quality category incurs
     * its full penalty. Scoring density rather than absolute count keeps large
     * codebases from being penalised simply for their size. Configurable via
     * {@code app.thresholds.code-smell-density-threshold} (default 70).
     */
    private final double smellDensityFullPenalty;

    public HealthScoreCalculator(
            @Value("${app.thresholds.code-smell-density-threshold:70}") double smellDensityFullPenalty) {
        this.smellDensityFullPenalty = smellDensityFullPenalty;
    }

    /**
     * Build a full breakdown from an already-persisted {@link AnalysisResult}.
     */
    public HealthScoreBreakdownResponse calculate(AnalysisResult result) {

        ScoreCategory antiPatterns  = buildAntiPatternCategory(result);
        ScoreCategory codeQuality   = buildCodeQualityCategory(result);
        ScoreCategory architecture  = buildArchitectureCategory(result);
        ScoreCategory serviceSizing = buildServiceSizingCategory(result);

        int overall = antiPatterns.score() + codeQuality.score()
                    + architecture.score() + serviceSizing.score();
        overall = Math.max(0, Math.min(100, overall));

        String grade = toGrade(overall);

        return new HealthScoreBreakdownResponse(
                overall,
                grade,
                List.of(antiPatterns, codeQuality, architecture, serviceSizing)
        );
    }


    private ScoreCategory buildAntiPatternCategory(AnalysisResult result) {
        List<Deduction> deductions = new ArrayList<>();
        int totalPenalty = 0;

        // Nano/God services are scored exclusively in the Service Sizing category,
        // so they are excluded here to avoid penalising them in two categories.
        Map<AntiPatternType, List<DetectedAntiPattern>> byType =
                result.getDetectedAntiPatterns().stream()
                        .filter(p -> p.getPatternType() != AntiPatternType.NANO_SERVICE
                                && p.getPatternType() != AntiPatternType.GOD_SERVICE)
                        .collect(Collectors.groupingBy(DetectedAntiPattern::getPatternType));

        for (var entry : byType.entrySet()) {
            AntiPatternType type = entry.getKey();
            List<DetectedAntiPattern> patterns = entry.getValue();

            int penalty = patterns.stream()
                    .mapToInt(p -> SEVERITY_PENALTY.getOrDefault(p.getSeverity(), 3))
                    .sum();

            totalPenalty += penalty;
            deductions.add(new Deduction(
                    type.getDisplayName() + " (×" + patterns.size() + ")",
                    patterns.size(),
                    penalty
            ));
        }

        int score = Math.max(0, ANTI_PATTERN_MAX - totalPenalty);
        return new ScoreCategory(
                "Anti-Patterns",
                "Detected architectural anti-patterns weighted by severity",
                score, ANTI_PATTERN_MAX, deductions);
    }

    private ScoreCategory buildCodeQualityCategory(AnalysisResult result) {
        List<Deduction> deductions = new ArrayList<>();
        int totalPenalty = 0;

        int totalSmells = result.getTotalCodeSmells();
        int totalLoc = result.getTotalLinesOfCode() != null ? result.getTotalLinesOfCode() : 0;
        if (totalSmells > 0 && totalLoc > 0) {
            // Score smell *density* (smells per 1000 LOC) rather than absolute count,
            // so a large codebase is not penalised simply for being large. The penalty
            // scales linearly with density and reaches CODE_QUALITY_MAX at
            // smellDensityFullPenalty smells/KLOC.
            double density = totalSmells / (totalLoc / 1000.0);
            int penalty = (int) Math.min(CODE_QUALITY_MAX,
                    Math.round(CODE_QUALITY_MAX * density / smellDensityFullPenalty));
            totalPenalty += penalty;
            deductions.add(new Deduction(
                    String.format("%d code smell(s), %.1f per KLOC", totalSmells, density),
                    totalSmells,
                    penalty
            ));
        }

        int score = Math.max(0, CODE_QUALITY_MAX - totalPenalty);
        return new ScoreCategory(
                "Code Quality",
                "Code smell density from static analysis",
                score, CODE_QUALITY_MAX, deductions);
    }

    private ScoreCategory buildArchitectureCategory(AnalysisResult result) {
        List<Deduction> deductions = new ArrayList<>();
        int totalPenalty = 0;

        double coupling = result.getCouplingCoefficient();
        if (coupling > 0.1) {
            int penalty = (int) Math.min(15, Math.round(coupling * 15));
            totalPenalty += penalty;
            deductions.add(new Deduction(
                    String.format("Coupling coefficient %.2f", coupling),
                    1,
                    penalty
            ));
        }

        int cycles = result.getCycleCount();
        if (cycles > 0) {
            int penalty = Math.min(10, cycles * 5);
            totalPenalty += penalty;
            deductions.add(new Deduction(
                    cycles + " dependency cycle(s)",
                    cycles,
                    penalty
            ));
        }

        int score = Math.max(0, ARCHITECTURE_MAX - totalPenalty);
        return new ScoreCategory(
                "Architecture",
                "Coupling between services and dependency structure",
                score, ARCHITECTURE_MAX, deductions);
    }

    private ScoreCategory buildServiceSizingCategory(AnalysisResult result) {
        List<Deduction> deductions = new ArrayList<>();
        int totalPenalty = 0;

        long nanoCount = result.getDetectedAntiPatterns().stream()
                .filter(p -> p.getPatternType() == AntiPatternType.NANO_SERVICE).count();
        long godCount = result.getDetectedAntiPatterns().stream()
                .filter(p -> p.getPatternType() == AntiPatternType.GOD_SERVICE).count();

        if (nanoCount > 0) {
            int penalty = (int) Math.min(8, nanoCount * 3);
            totalPenalty += penalty;
            deductions.add(new Deduction(
                    nanoCount + " nano service(s)",
                    (int) nanoCount,
                    penalty
            ));
        }

        if (godCount > 0) {
            int penalty = (int) Math.min(10, godCount * 5);
            totalPenalty += penalty;
            deductions.add(new Deduction(
                    godCount + " god service(s)",
                    (int) godCount,
                    penalty
            ));
        }

        int score = Math.max(0, SERVICE_SIZING_MAX - totalPenalty);
        return new ScoreCategory(
                "Service Sizing",
                "Appropriateness of individual service sizes",
                score, SERVICE_SIZING_MAX, deductions);
    }


    private static String toGrade(int score) {
        if (score >= 90) return "A";
        if (score >= 80) return "B";
        if (score >= 65) return "C";
        if (score >= 50) return "D";
        return "F";
    }
}

