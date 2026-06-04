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

        List<AntiPatternChange> resolved = new ArrayList<>();
        List<AntiPatternChange> newOnes = new ArrayList<>();
        List<AntiPatternChange> unchanged = new ArrayList<>();

        Map<String, Deque<DetectedAntiPattern>> previousByIssue = indexByIssueKey(previous);
        for (DetectedAntiPattern currentPattern : safePatterns(current)) {
            Deque<DetectedAntiPattern> matchingPrevious =
                    previousByIssue.get(issueKey(currentPattern));
            if (matchingPrevious != null && !matchingPrevious.isEmpty()) {
                matchingPrevious.removeFirst();
                unchanged.add(toChange(currentPattern));
            } else {
                newOnes.add(toChange(currentPattern));
            }
        }
        previousByIssue.values().stream()
                .flatMap(Collection::stream)
                .map(this::toChange)
                .forEach(resolved::add);

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

    private Map<String, Deque<DetectedAntiPattern>> indexByIssueKey(AnalysisResult result) {
        Map<String, Deque<DetectedAntiPattern>> byKey = new LinkedHashMap<>();
        for (DetectedAntiPattern pattern : safePatterns(result)) {
            byKey.computeIfAbsent(issueKey(pattern), _ -> new ArrayDeque<>()).add(pattern);
        }
        return byKey;
    }

    private List<DetectedAntiPattern> safePatterns(AnalysisResult result) {
        if (result.getDetectedAntiPatterns() == null) {
            return Collections.emptyList();
        }
        return result.getDetectedAntiPatterns();
    }

    private String issueKey(DetectedAntiPattern ap) {
        AntiPatternType type = ap.getPatternType();
        return type.name()
                + "|primary=" + normalize(primaryServiceName(ap))
                + "|affected=" + canonicalList(parseJsonList(ap.getAffectedServicesJson()))
                + "|signature=" + issueSignature(ap);
    }

    private String issueSignature(DetectedAntiPattern ap) {
        return switch (ap.getPatternType()) {
            case HARDCODED_ENDPOINTS -> canonicalList(readListField(ap.getEvidenceJson(), "url"));
            case GOD_SERVICE -> canonicalList(readNestedListField(ap.getDetailsJson(), "godClasses", "className"));
            case SHARED_DATABASE -> normalize(ap.getSharedDatabaseUrl());
            case CHATTY_SERVICE -> normalize(readStringField(ap.getDetailsJson(), "chattyType"));
            case CYCLIC_DEPENDENCY -> String.valueOf(ap.getCycleLength());
            default -> "";
        };
    }

    private String primaryServiceName(DetectedAntiPattern ap) {
        return ap.getPrimaryService() != null ? ap.getPrimaryService().getName() : "";
    }

    private String canonicalList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(this::normalize)
                .filter(value -> !value.isBlank())
                .sorted()
                .collect(Collectors.joining(","));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private List<String> readListField(String json, String fieldName) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<Map<String, Object>> entries = objectMapper.readValue(json, new TypeReference<>() {});
            return entries.stream()
                    .map(entry -> entry.get(fieldName))
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .toList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private List<String> readNestedListField(String json, String listField, String valueField) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            Map<String, Object> details = objectMapper.readValue(json, new TypeReference<>() {});
            Object rawList = details.get(listField);
            if (!(rawList instanceof List<?> entries)) {
                return Collections.emptyList();
            }
            List<String> values = new ArrayList<>();
            for (Object entry : entries) {
                if (entry instanceof Map<?, ?> map) {
                    Object value = map.get(valueField);
                    if (value != null) {
                        values.add(value.toString());
                    }
                }
            }
            return values;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private String readStringField(String json, String fieldName) {
        if (json == null || json.isBlank()) {
            return "";
        }
        try {
            Map<String, Object> details = objectMapper.readValue(json, new TypeReference<>() {});
            Object value = details.get(fieldName);
            return value != null ? value.toString() : "";
        } catch (Exception e) {
            return "";
        }
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

