package com.msadetector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msadetector.entity.*;
import com.msadetector.enums.AntiPatternType;
import com.msadetector.enums.Severity;
import com.msadetector.repository.*;
import com.msadetector.service.detection.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates anti-pattern detection by delegating to individual
 * {@link AntiPatternDetector} implementations. Also builds the
 * dependency graph via {@link DependencyGraphBuilder} and assembles
 * the final {@link AnalysisResult}.
 */
@Service
public class AntiPatternDetectorService {

    private static final Logger log = LoggerFactory.getLogger(AntiPatternDetectorService.class);

    private final DependencyGraphBuilder dependencyGraphBuilder;
    private final List<AntiPatternDetector> detectors;
    private final MicroserviceRepository microserviceRepository;
    private final ServiceDependencyRepository dependencyRepository;
    private final CodeSmellRepository codeSmellRepository;
    private final AnalysisResultRepository resultRepository;
    private final ObjectMapper objectMapper;
    private final HealthScoreCalculator healthScoreCalculator;

    public AntiPatternDetectorService(
            DependencyGraphBuilder dependencyGraphBuilder,
            List<AntiPatternDetector> detectors,
            MicroserviceRepository microserviceRepository,
            ServiceDependencyRepository dependencyRepository,
            CodeSmellRepository codeSmellRepository,
            AnalysisResultRepository resultRepository,
            ObjectMapper objectMapper,
            HealthScoreCalculator healthScoreCalculator
    ) {
        this.dependencyGraphBuilder = dependencyGraphBuilder;
        this.detectors = detectors;
        this.microserviceRepository = microserviceRepository;
        this.dependencyRepository = dependencyRepository;
        this.codeSmellRepository = codeSmellRepository;
        this.resultRepository = resultRepository;
        this.objectMapper = objectMapper;
        this.healthScoreCalculator = healthScoreCalculator;
    }

    /**
     * Delegates to {@link DependencyGraphBuilder} to scan source code
     * and populate endpoints, datasource URLs, and service dependencies.
     */
    public void buildDependencyGraph(Project project) {
        dependencyGraphBuilder.buildDependencyGraph(project);
    }

    /**
     * Runs all registered {@link AntiPatternDetector} implementations and
     * assembles the final {@link AnalysisResult}.
     */
    public AnalysisResult detectAntiPatterns(Project project, AnalysisJob job) {
        List<Microservice> microservices = microserviceRepository.findByProject(project);
        List<DetectedAntiPattern> antiPatterns = new ArrayList<>();

        for (AntiPatternDetector detector : detectors) {
            if (!isDetectorEnabled(detector, job)) {
                log.info("Skipping {} (disabled for this job)",
                        detector.getClass().getSimpleName());
                continue;
            }
            try {
                List<DetectedAntiPattern> detected = detector.detect(project, microservices);
                antiPatterns.addAll(detected);
                log.info("{} found {} anti-pattern(s)",
                        detector.getClass().getSimpleName(), detected.size());
            } catch (Exception e) {
                log.error("Detector {} failed: {}", detector.getClass().getSimpleName(), e.getMessage());
            }
        }

        int totalCodeSmells = codeSmellRepository.countByProject(project);
        int totalLoc = microservices.stream().mapToInt(Microservice::getLinesOfCode).sum();
        double avgSize = microservices.isEmpty() ? 0 : (double) totalLoc / microservices.size();

        int criticalCount = (int) antiPatterns.stream().filter(ap -> ap.getSeverity() == Severity.CRITICAL).count();
        int highCount = (int) antiPatterns.stream().filter(ap -> ap.getSeverity() == Severity.HIGH).count();
        int mediumCount = (int) antiPatterns.stream().filter(ap -> ap.getSeverity() == Severity.MEDIUM).count();
        int lowCount = (int) antiPatterns.stream().filter(ap -> ap.getSeverity() == Severity.LOW).count();

        List<ServiceDependency> allDeps = dependencyRepository.findByProject(project);

        int cycleCount = (int) antiPatterns.stream()
                .filter(ap -> ap.getPatternType() == AntiPatternType.CYCLIC_DEPENDENCY)
                .count();

        int n = microservices.size();
        int maxPossibleEdges = n > 1 ? n * (n - 1) : 1;
        Set<String> uniqueEdges = allDeps.stream()
                .map(dep -> dep.getSourceService().getId() + "->" + dep.getTargetService().getId())
                .collect(Collectors.toSet());
        double couplingCoefficient = (double) uniqueEdges.size() / maxPossibleEdges;

        AnalysisResult result = AnalysisResult.builder()
                .analysisJob(job)
                .servicesAnalyzed(microservices.size())
                .totalAntiPatterns(antiPatterns.size())
                .totalCodeSmells(totalCodeSmells)
                .criticalIssues(criticalCount)
                .highIssues(highCount)
                .mediumIssues(mediumCount)
                .lowIssues(lowCount)
                .totalLinesOfCode(totalLoc)
                .averageServiceSize(avgSize)
                .totalDependencies(allDeps.size())
                .cycleCount(cycleCount)
                .couplingCoefficient(couplingCoefficient)
                .dependencyGraphJson(buildGraphJson(microservices, project))
                .build();

        for (DetectedAntiPattern ap : antiPatterns) {
            result.addAntiPattern(ap);
        }

        result.setHealthScore(healthScoreCalculator.calculate(result).overallScore());

        return resultRepository.save(result);
    }

    private boolean isDetectorEnabled(AntiPatternDetector detector, AnalysisJob job) {
        return switch (detector) {
            case CyclicDependencyDetector _ -> job.isDetectCyclicDependencies();
            case SharedDatabaseDetector _ -> job.isDetectSharedDatabases();
            case NanoServiceDetector _ -> job.isDetectNanoServices();
            case GodServiceDetector _ -> job.isDetectGodServices();
            case ChattyServiceDetector _ -> job.isDetectChattyServices();
            case HardcodedEndpointDetector _ -> job.isDetectHardcodedEndpoints();
            default -> true;
        };
    }

    private String buildGraphJson(List<Microservice> microservices, Project project) {
        try {
            List<Map<String, Object>> nodes = microservices.stream()
                    .map(ms -> Map.<String, Object>of(
                            "id", ms.getId().toString(),
                            "name", ms.getName(),
                            "linesOfCode", ms.getLinesOfCode()
                    ))
                    .toList();

            List<ServiceDependency> dependencies = dependencyRepository.findByProject(project);
            List<Map<String, Object>> edges = dependencies.stream()
                    .map(dep -> Map.<String, Object>of(
                            "source", dep.getSourceService().getId().toString(),
                            "target", dep.getTargetService().getId().toString(),
                            "type", dep.getDependencyType().name(),
                            "weight", dep.getCallCount()
                    ))
                    .toList();

            return objectMapper.writeValueAsString(Map.of("nodes", nodes, "edges", edges));
        } catch (Exception e) {
            return "{}";
        }
    }
}
