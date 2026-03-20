package com.msadetector.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msadetector.dto.AnalysisDiffResponse;
import com.msadetector.dto.AnalysisDiffResponse.*;
import com.msadetector.dto.HealthScoreBreakdownResponse;
import com.msadetector.entity.AnalysisResult;
import com.msadetector.entity.DetectedAntiPattern;
import com.msadetector.enums.AntiPatternType;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes the diff between two {@link AnalysisResult} instances belonging
 * to the same project — the "New Code" feature inspired by SonarQube.
 */
@Service
public class AnalysisDiffService {

    private final HealthScoreCalculator healthScoreCalculator;
    private final ObjectMapper objectMapper;

    public AnalysisDiffService(HealthScoreCalculator healthScoreCalculator,
                               ObjectMapper objectMapper) {
        this.healthScoreCalculator = healthScoreCalculator;
        this.objectMapper = objectMapper;
    }

    /**
     * Build a diff between the current and previous analysis result.
     *
     * @param current       the latest analysis result (must have anti-patterns loaded)
     * @param previous      the previous analysis result (must have anti-patterns loaded)
     * @param analysisNumber which run this is (1-based)
     * @return a full diff DTO
     */
    public AnalysisDiffResponse buildDiff(AnalysisResult current, AnalysisResult previous, int analysisNumber) {

        HealthScoreBreakdownResponse currentBreakdown = healthScoreCalculator.calculate(current);
        HealthScoreBreakdownResponse previousBreakdown = healthScoreCalculator.calculate(previous);

        int currentScore = currentBreakdown.overallScore();
        int previousScore = previousBreakdown.overallScore();
        int delta = currentScore - previousScore;

        Map<AntiPatternType, List<DetectedAntiPattern>> prevByType =
                previous.getDetectedAntiPatterns().stream()
                        .collect(Collectors.groupingBy(DetectedAntiPattern::getPatternType));
        Map<AntiPatternType, List<DetectedAntiPattern>> currByType =
                current.getDetectedAntiPatterns().stream()
                        .collect(Collectors.groupingBy(DetectedAntiPattern::getPatternType));

        Set<AntiPatternType> allTypes = new HashSet<>();
        allTypes.addAll(prevByType.keySet());
        allTypes.addAll(currByType.keySet());

        List<AntiPatternChange> resolved = new ArrayList<>();
        List<AntiPatternChange> newOnes = new ArrayList<>();
        List<AntiPatternChange> unchanged = new ArrayList<>();

        for (AntiPatternType type : allTypes) {
            List<DetectedAntiPattern> prevList = prevByType.getOrDefault(type, Collections.emptyList());
            List<DetectedAntiPattern> currList = currByType.getOrDefault(type, Collections.emptyList());

            if (!prevList.isEmpty() && currList.isEmpty()) {
                for (DetectedAntiPattern ap : prevList) {
                    resolved.add(toChange(ap));
                }
            } else if (prevList.isEmpty() && !currList.isEmpty()) {
                for (DetectedAntiPattern ap : currList) {
                    newOnes.add(toChange(ap));
                }
            } else {
                int prevCount = prevList.size();
                int currCount = currList.size();

                int commonCount = Math.min(prevCount, currCount);
                for (int i = 0; i < commonCount; i++) {
                    unchanged.add(toChange(currList.get(i)));
                }
                for (int i = commonCount; i < currCount; i++) {
                    newOnes.add(toChange(currList.get(i)));
                }
                for (int i = commonCount; i < prevCount; i++) {
                    resolved.add(toChange(prevList.get(i)));
                }
            }
        }

        List<CategoryDelta> categoryDeltas = buildCategoryDeltas(previousBreakdown, currentBreakdown);

        String summary = buildSummary(
                previousScore, previousBreakdown.grade(),
                currentScore, currentBreakdown.grade(),
                resolved.size(), newOnes.size()
        );

        return new AnalysisDiffResponse(
                current.getAnalysisJob().getId(),
                previous.getAnalysisJob().getId(),
                current.getCreatedAt(),
                previous.getCreatedAt(),
                analysisNumber,

                currentScore,
                previousScore,
                delta,
                currentBreakdown.grade(),
                previousBreakdown.grade(),

                intDelta(previous.getTotalAntiPatterns(), current.getTotalAntiPatterns()),
                intDelta(previous.getTotalCodeSmells(), current.getTotalCodeSmells()),
                intDelta(previous.getCriticalIssues(), current.getCriticalIssues()),
                intDelta(previous.getHighIssues(), current.getHighIssues()),
                intDelta(previous.getMediumIssues(), current.getMediumIssues()),
                intDelta(previous.getLowIssues(), current.getLowIssues()),
                intDelta(previous.getTotalLinesOfCode(), current.getTotalLinesOfCode()),
                intDelta(previous.getServicesAnalyzed(), current.getServicesAnalyzed()),
                intDelta(previous.getCycleCount(), current.getCycleCount()),
                intDelta(previous.getTotalDependencies(), current.getTotalDependencies()),
                doubleDelta(previous.getCouplingCoefficient(), current.getCouplingCoefficient()),

                resolved,
                newOnes,
                unchanged,
                categoryDeltas,
                summary
        );
    }

    private AntiPatternChange toChange(DetectedAntiPattern ap) {
        List<String> services = parseJsonList(ap.getAffectedServicesJson());
        return new AntiPatternChange(
                ap.getPatternType(),
                ap.getSeverity(),
                ap.getDescription(),
                services
        );
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private MetricDelta intDelta(int prev, int curr) {
        return new MetricDelta(prev, curr, curr - prev);
    }

    private DoubleDelta doubleDelta(double prev, double curr) {
        return new DoubleDelta(prev, curr, curr - prev);
    }

    private List<CategoryDelta> buildCategoryDeltas(
            HealthScoreBreakdownResponse prev,
            HealthScoreBreakdownResponse curr
    ) {
        List<CategoryDelta> deltas = new ArrayList<>();
        var prevCats = prev.categories();
        var currCats = curr.categories();

        for (int i = 0; i < currCats.size(); i++) {
            var pc = i < prevCats.size() ? prevCats.get(i) : null;
            var cc = currCats.get(i);
            deltas.add(new CategoryDelta(
                    cc.name(),
                    pc != null ? pc.score() : cc.maxScore(),
                    cc.score(),
                    cc.maxScore(),
                    cc.score() - (pc != null ? pc.score() : cc.maxScore())
            ));
        }
        return deltas;
    }

    private String buildSummary(int prevScore, String prevGrade,
                                int currScore, String currGrade,
                                int resolvedCount, int newCount) {
        StringBuilder sb = new StringBuilder();

        if (currScore > prevScore) {
            sb.append(String.format("Health improved from %d (%s) → %d (%s).", prevScore, prevGrade, currScore, currGrade));
        } else if (currScore < prevScore) {
            sb.append(String.format("Health declined from %d (%s) → %d (%s).", prevScore, prevGrade, currScore, currGrade));
        } else {
            sb.append(String.format("Health unchanged at %d (%s).", currScore, currGrade));
        }

        if (resolvedCount > 0) {
            sb.append(String.format(" %d anti-pattern(s) resolved.", resolvedCount));
        }
        if (newCount > 0) {
            sb.append(String.format(" %d new anti-pattern(s) detected.", newCount));
        }
        if (resolvedCount == 0 && newCount == 0) {
            sb.append(" No anti-pattern changes.");
        }

        return sb.toString();
    }
}

