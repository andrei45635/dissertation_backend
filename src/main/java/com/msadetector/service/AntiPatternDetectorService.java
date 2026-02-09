package com.msadetector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msadetector.entity.*;
import com.msadetector.enums.AntiPatternType;
import com.msadetector.enums.Severity;
import com.msadetector.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AntiPatternDetectorService {

    private final MicroserviceRepository microserviceRepository;
    private final ServiceDependencyRepository dependencyRepository;
    private final CodeSmellRepository codeSmellRepository;
    private final AnalysisResultRepository resultRepository;
    private final ObjectMapper objectMapper;

    private final int nanoServiceMaxLoc;
    private final int nanoServiceMaxEndpoints;
    private final int godServiceMinDomains;

    public AntiPatternDetectorService(
            MicroserviceRepository microserviceRepository,
            ServiceDependencyRepository dependencyRepository,
            CodeSmellRepository codeSmellRepository,
            AnalysisResultRepository resultRepository,
            ObjectMapper objectMapper,
            @Value("${app.thresholds.nano-service-max-loc:500}") int nanoServiceMaxLoc,
            @Value("${app.thresholds.nano-service-max-endpoints:2}") int nanoServiceMaxEndpoints,
            @Value("${app.thresholds.god-service-min-domains:3}") int godServiceMinDomains
    ) {
        this.microserviceRepository = microserviceRepository;
        this.dependencyRepository = dependencyRepository;
        this.codeSmellRepository = codeSmellRepository;
        this.resultRepository = resultRepository;
        this.objectMapper = objectMapper;
        this.nanoServiceMaxLoc = nanoServiceMaxLoc;
        this.nanoServiceMaxEndpoints = nanoServiceMaxEndpoints;
        this.godServiceMinDomains = godServiceMinDomains;
    }

    public void buildDependencyGraph(Project project) {
        // TODO: Implement REST client detection using Spoon
        // This would scan for @FeignClient, RestTemplate, WebClient usages
    }

    public AnalysisResult detectAntiPatterns(Project project, AnalysisJob job) {
        List<Microservice> microservices = microserviceRepository.findByProject(project);
        List<DetectedAntiPattern> antiPatterns = new ArrayList<>();

        antiPatterns.addAll(detectCyclicDependencies(project));
        antiPatterns.addAll(detectSharedDatabases(project, microservices));
        antiPatterns.addAll(detectNanoServices(project, microservices));
        antiPatterns.addAll(detectGodServices(project, microservices));

        int totalCodeSmells = codeSmellRepository.countByProject(project);
        int totalLoc = microservices.stream().mapToInt(Microservice::getLinesOfCode).sum();
        double avgSize = microservices.isEmpty() ? 0 : (double) totalLoc / microservices.size();

        int criticalCount = (int) antiPatterns.stream().filter(ap -> ap.getSeverity() == Severity.CRITICAL).count();
        int highCount = (int) antiPatterns.stream().filter(ap -> ap.getSeverity() == Severity.HIGH).count();
        int mediumCount = (int) antiPatterns.stream().filter(ap -> ap.getSeverity() == Severity.MEDIUM).count();
        int lowCount = (int) antiPatterns.stream().filter(ap -> ap.getSeverity() == Severity.LOW).count();

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
                .dependencyGraphJson(buildGraphJson(microservices, project))
                .build();

        result.calculateHealthScore();

        for (DetectedAntiPattern ap : antiPatterns) {
            result.addAntiPattern(ap);
        }

        return resultRepository.save(result);
    }

    private List<DetectedAntiPattern> detectCyclicDependencies(Project project) {
        // TODO: Implement Tarjan's algorithm for cycle detection
        return List.of();
    }

    private List<DetectedAntiPattern> detectSharedDatabases(Project project, List<Microservice> microservices) {
        List<DetectedAntiPattern> patterns = new ArrayList<>();

        Map<String, List<Microservice>> byDatasource = microservices.stream()
                .filter(ms -> ms.getDatasourceUrl() != null && !ms.getDatasourceUrl().isBlank())
                .collect(Collectors.groupingBy(Microservice::getDatasourceUrl));

        for (Map.Entry<String, List<Microservice>> entry : byDatasource.entrySet()) {
            if (entry.getValue().size() > 1) {
                List<String> serviceNames = entry.getValue().stream()
                        .map(Microservice::getName)
                        .toList();

                DetectedAntiPattern pattern = DetectedAntiPattern.builder()
                        .patternType(AntiPatternType.SHARED_DATABASE)
                        .severity(Severity.HIGH)
                        .description("Multiple services share the same database: " + entry.getKey())
                        .affectedServicesJson(toJson(serviceNames))
                        .sharedDatabaseUrl(entry.getKey())
                        .remediation("Consider database-per-service pattern with data synchronization via events")
                        .build();

                patterns.add(pattern);
            }
        }

        return patterns;
    }

    private List<DetectedAntiPattern> detectNanoServices(Project project, List<Microservice> microservices) {
        List<DetectedAntiPattern> patterns = new ArrayList<>();

        for (Microservice ms : microservices) {
            if (ms.getLinesOfCode() < nanoServiceMaxLoc && ms.getNumberOfEndpoints() <= nanoServiceMaxEndpoints) {
                DetectedAntiPattern pattern = DetectedAntiPattern.builder()
                        .patternType(AntiPatternType.NANO_SERVICE)
                        .severity(Severity.MEDIUM)
                        .description(String.format(
                                "Service '%s' is too small (%d LOC, %d endpoints) to justify operational overhead",
                                ms.getName(), ms.getLinesOfCode(), ms.getNumberOfEndpoints()
                        ))
                        .affectedServicesJson(toJson(List.of(ms.getName())))
                        .primaryService(ms)
                        .remediation("Consider merging with a related service")
                        .build();

                patterns.add(pattern);
            }
        }

        return patterns;
    }

    private List<DetectedAntiPattern> detectGodServices(Project project, List<Microservice> microservices) {
        List<DetectedAntiPattern> patterns = new ArrayList<>();

        for (Microservice ms : microservices) {
            List<CodeSmell> godClassSmells = codeSmellRepository.findByMicroservice(ms).stream()
                    .filter(smell -> "God Class".equalsIgnoreCase(smell.getSmellType()))
                    .toList();

            if (godClassSmells.size() >= godServiceMinDomains) {
                DetectedAntiPattern pattern = DetectedAntiPattern.builder()
                        .patternType(AntiPatternType.GOD_SERVICE)
                        .severity(Severity.HIGH)
                        .description(String.format(
                                "Service '%s' has %d God Class smells, indicating too many responsibilities",
                                ms.getName(), godClassSmells.size()
                        ))
                        .affectedServicesJson(toJson(List.of(ms.getName())))
                        .primaryService(ms)
                        .remediation("Consider decomposing into smaller, focused services")
                        .build();

                patterns.add(pattern);
            }
        }

        return patterns;
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

    private String toJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }
}
