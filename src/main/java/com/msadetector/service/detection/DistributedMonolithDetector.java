package com.msadetector.service.detection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msadetector.entity.AnalysisJob;
import com.msadetector.entity.DetectedAntiPattern;
import com.msadetector.entity.Microservice;
import com.msadetector.entity.Project;
import com.msadetector.entity.ServiceDependency;
import com.msadetector.enums.AntiPatternType;
import com.msadetector.enums.Severity;
import com.msadetector.repository.ServiceDependencyRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Detects distributed monolith by analyzing coupling metrics.
 * A distributed monolith is indicated when services are so tightly
 * coupled that they cannot be deployed independently.
 * <p>
 * Uses coupling coefficient = actual edges / (n * (n-1)) for a directed graph,
 * connected service ratio, and shared database count as indicators.
 */
@Component
public class DistributedMonolithDetector extends BaseDetector {

    private final ServiceDependencyRepository dependencyRepository;
    private final double highCouplingThreshold;
    private final double connectedRatioThreshold;
    private final double moderateCouplingThreshold;

    public DistributedMonolithDetector(ObjectMapper objectMapper,
                                        ServiceDependencyRepository dependencyRepository,
                                        @Value("${app.thresholds.distributed-monolith-high-coupling:0.5}") double highCouplingThreshold,
                                        @Value("${app.thresholds.distributed-monolith-connected-ratio:0.8}") double connectedRatioThreshold,
                                        @Value("${app.thresholds.distributed-monolith-moderate-coupling:0.3}") double moderateCouplingThreshold) {
        super(objectMapper);
        this.dependencyRepository = dependencyRepository;
        this.highCouplingThreshold = highCouplingThreshold;
        this.connectedRatioThreshold = connectedRatioThreshold;
        this.moderateCouplingThreshold = moderateCouplingThreshold;
    }

    @Override
    public List<DetectedAntiPattern> detect(Project project, List<Microservice> microservices, AnalysisJob job) {
        List<DetectedAntiPattern> patterns = new ArrayList<>();

        if (microservices.size() < 3) {
            return patterns;
        }

        List<ServiceDependency> allDeps = job != null
                ? dependencyRepository.findByAnalysisJobWithServices(job)
                : dependencyRepository.findByProjectWithServices(project);

        int n = microservices.size();
        int maxPossibleEdges = n * (n - 1);
        double effectiveHighCouplingThreshold = job != null && job.getDistributedMonolithHighCoupling() != null
                ? job.getDistributedMonolithHighCoupling()
                : highCouplingThreshold;
        double effectiveConnectedRatioThreshold = job != null && job.getDistributedMonolithConnectedRatio() != null
                ? job.getDistributedMonolithConnectedRatio()
                : connectedRatioThreshold;
        double effectiveModerateCouplingThreshold = job != null && job.getDistributedMonolithModerateCoupling() != null
                ? job.getDistributedMonolithModerateCoupling()
                : moderateCouplingThreshold;

        Set<String> uniqueEdges = allDeps.stream()
                .map(dep -> dep.getSourceService().getId() + "->" + dep.getTargetService().getId())
                .collect(Collectors.toSet());

        int actualEdges = uniqueEdges.size();
        double couplingCoefficient = maxPossibleEdges > 0 ? (double) actualEdges / maxPossibleEdges : 0;

        Set<Long> connectedServiceIds = new HashSet<>();
        for (ServiceDependency dep : allDeps) {
            connectedServiceIds.add(dep.getSourceService().getId());
            connectedServiceIds.add(dep.getTargetService().getId());
        }
        double connectedRatio = (double) connectedServiceIds.size() / n;

        long sharedDbCount = microservices.stream()
                .filter(ms -> ms.getDatasourceUrl() != null && !ms.getDatasourceUrl().isBlank())
                .collect(Collectors.groupingBy(Microservice::getDatasourceUrl))
                .values().stream()
                .filter(list -> list.size() > 1)
                .count();

        boolean isDistributedMonolith = couplingCoefficient > effectiveHighCouplingThreshold
                || (connectedRatio > effectiveConnectedRatioThreshold && sharedDbCount > 0)
                || (connectedRatio > effectiveConnectedRatioThreshold
                        && couplingCoefficient > effectiveModerateCouplingThreshold);

        if (isDistributedMonolith) {
            List<String> allServiceNames = microservices.stream().map(Microservice::getName).toList();
            Path projectRoot = Path.of(project.getLocalPath());

            List<Map<String, Object>> snippets = allDeps.stream()
                    .filter(dep -> dep.getEvidenceFile() != null)
                    .limit(5)
                    .map(dep -> {
                        if (dep.getEvidenceLine() != null && dep.getEvidenceLine() > 0) {
                            return readSnippet(projectRoot.resolve(dep.getEvidenceFile()), dep.getEvidenceLine());
                        }
                        return buildSnippet(dep.getEvidenceFile(),
                                dep.getEvidenceLine() != null ? dep.getEvidenceLine() : 0,
                                dep.getEvidenceCode());
                    })
                    .toList();

            DetectedAntiPattern pattern = DetectedAntiPattern.builder()
                    .patternType(AntiPatternType.DISTRIBUTED_MONOLITH)
                    .severity(Severity.CRITICAL)
                    .description(String.format(
                            "The system exhibits distributed monolith characteristics. "
                                    + "Coupling coefficient: %.2f (%d/%d possible edges), "
                                    + "%.0f%% of services are interconnected, "
                                    + "%d shared database(s) detected. "
                                    + "Services are tightly coupled and likely need coordinated deployments.",
                            couplingCoefficient, actualEdges, maxPossibleEdges,
                            connectedRatio * 100, sharedDbCount
                    ))
                    .affectedServicesJson(toJson(allServiceNames))
                    .detailsJson(toJson(Map.of(
                            "couplingCoefficient", couplingCoefficient,
                            "actualEdges", actualEdges,
                            "maxPossibleEdges", maxPossibleEdges,
                            "connectedServices", connectedServiceIds.size(),
                            "totalServices", n,
                            "connectedRatio", connectedRatio,
                            "sharedDatabases", sharedDbCount
                    )))
                    .codeSnippetsJson(snippetsToJson(snippets))
                    .remediation("Reduce inter-service coupling by: introducing asynchronous event-driven communication, "
                            + "applying database-per-service pattern, removing synchronous call chains, "
                            + "and ensuring services can be deployed independently")
                    .build();

            patterns.add(pattern);
            log.info("Distributed monolith detected: coupling={}, connected={}%, sharedDBs={}",
                    couplingCoefficient, connectedRatio * 100, sharedDbCount);
        }

        return patterns;
    }
}
