package com.msadetector.service.detection;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Detects ESB (Enterprise Service Bus) Misuse — a single service
 * that mediates most inter-service communication, acting as a central
 * hub through which all requests are routed.
 * <p>
 * This anti-pattern negates the benefits of microservices by creating
 * a single point of failure and bottleneck, similar to a monolithic
 * ESB in a SOA architecture.
 * <p>
 * Detection heuristic: if a single service handles more than
 * a configurable percentage of all incoming dependencies, it is
 * flagged as an ESB-like mediator.
 */
@Component
public class EsbMisuseDetector extends BaseDetector {

    private static final Set<String> GATEWAY_KEYWORDS = Set.of(
            "gateway", "api-gateway", "apigateway", "edge", "edge-service",
            "zuul", "proxy", "bff", "ingress", "router"
    );

    private final ServiceDependencyRepository dependencyRepository;
    private final double mediatorThreshold;

    public EsbMisuseDetector(ObjectMapper objectMapper,
                              ServiceDependencyRepository dependencyRepository,
                              @Value("${app.thresholds.esb-mediator-threshold:0.4}") double mediatorThreshold) {
        super(objectMapper);
        this.dependencyRepository = dependencyRepository;
        this.mediatorThreshold = mediatorThreshold;
    }

    @Override
    public List<DetectedAntiPattern> detect(Project project, List<Microservice> microservices) {
        List<DetectedAntiPattern> patterns = new ArrayList<>();

        if (microservices.size() < 3) {
            return patterns;
        }

        List<ServiceDependency> allDeps = dependencyRepository.findByProjectWithServices(project);
        if (allDeps.isEmpty()) {
            return patterns;
        }

        int totalDependencies = allDeps.size();

        Map<Long, List<ServiceDependency>> incomingByService = allDeps.stream()
                .collect(Collectors.groupingBy(dep -> dep.getTargetService().getId()));

        Map<Long, List<ServiceDependency>> outgoingByService = allDeps.stream()
                .collect(Collectors.groupingBy(dep -> dep.getSourceService().getId()));

        Map<Long, String> serviceNames = new HashMap<>();
        for (Microservice ms : microservices) {
            serviceNames.put(ms.getId(), ms.getName());
        }

        for (Microservice ms : microservices) {
            Long msId = ms.getId();

            int incomingCount = incomingByService.getOrDefault(msId, List.of()).size();
            int outgoingCount = outgoingByService.getOrDefault(msId, List.of()).size();
            int totalThroughService = incomingCount + outgoingCount;

            double mediatorRatio = (double) totalThroughService / totalDependencies;

            Set<Long> uniqueCallers = incomingByService.getOrDefault(msId, List.of()).stream()
                    .map(dep -> dep.getSourceService().getId())
                    .collect(Collectors.toSet());

            Set<Long> uniqueCallees = outgoingByService.getOrDefault(msId, List.of()).stream()
                    .map(dep -> dep.getTargetService().getId())
                    .collect(Collectors.toSet());

            int otherServicesCount = microservices.size() - 1;
            double callerRatio = otherServicesCount > 0 ? (double) uniqueCallers.size() / otherServicesCount : 0;
            double calleeRatio = otherServicesCount > 0 ? (double) uniqueCallees.size() / otherServicesCount : 0;

            boolean isMediatorByConnections = callerRatio >= mediatorThreshold && calleeRatio >= mediatorThreshold;

            boolean isMediatorByVolume = mediatorRatio >= mediatorThreshold;

            boolean isLikelyGateway = GATEWAY_KEYWORDS.stream()
                    .anyMatch(kw -> ms.getName().toLowerCase().contains(kw));
            if (isLikelyGateway) {
                log.debug("Skipping service '{}' — likely an API gateway (not ESB misuse)", ms.getName());
                continue;
            }

            if (isMediatorByConnections || isMediatorByVolume) {
                Path projectRoot = Path.of(project.getLocalPath());

                List<String> callerNames = uniqueCallers.stream()
                        .map(id -> serviceNames.getOrDefault(id, "unknown"))
                        .sorted()
                        .toList();

                List<String> calleeNames = uniqueCallees.stream()
                        .map(id -> serviceNames.getOrDefault(id, "unknown"))
                        .sorted()
                        .toList();

                List<ServiceDependency> relevantDeps = new ArrayList<>();
                relevantDeps.addAll(incomingByService.getOrDefault(msId, List.of()));
                relevantDeps.addAll(outgoingByService.getOrDefault(msId, List.of()));

                List<Map<String, Object>> snippets = relevantDeps.stream()
                        .filter(dep -> dep.getEvidenceFile() != null)
                        .limit(5)
                        .map(dep -> {
                            if (dep.getEvidenceLine() != null && dep.getEvidenceLine() > 0) {
                                return readSnippet(projectRoot.resolve(dep.getEvidenceFile()), dep.getEvidenceLine());
                            }
                            return buildSnippet(dep.getEvidenceFile(), 0, dep.getEvidenceCode());
                        })
                        .toList();

                List<String> allAffected = new ArrayList<>();
                allAffected.add(ms.getName());
                allAffected.addAll(callerNames);
                allAffected.addAll(calleeNames);
                List<String> uniqueAffected = allAffected.stream().distinct().toList();

                DetectedAntiPattern pattern = DetectedAntiPattern.builder()
                        .patternType(AntiPatternType.ESB_MISUSE)
                        .severity(Severity.HIGH)
                        .description(String.format(
                                "Service '%s' acts as a central mediator (ESB-like hub). "
                                        + "It is called by %d service(s) (%.0f%%) and calls %d service(s) (%.0f%%), "
                                        + "handling %d of %d total dependencies. "
                                        + "This creates a single point of failure and bottleneck.",
                                ms.getName(),
                                uniqueCallers.size(), callerRatio * 100,
                                uniqueCallees.size(), calleeRatio * 100,
                                totalThroughService, totalDependencies
                        ))
                        .affectedServicesJson(toJson(uniqueAffected))
                        .primaryService(ms)
                        .detailsJson(toJson(Map.of(
                                "mediatorService", ms.getName(),
                                "incomingDependencies", incomingCount,
                                "outgoingDependencies", outgoingCount,
                                "uniqueCallers", callerNames,
                                "uniqueCallees", calleeNames,
                                "callerRatio", callerRatio,
                                "calleeRatio", calleeRatio,
                                "mediatorRatio", mediatorRatio
                        )))
                        .codeSnippetsJson(snippetsToJson(snippets))
                        .remediation("Eliminate the central mediator by having services communicate directly "
                                + "with each other using service discovery, or adopt choreography-based "
                                + "event-driven communication instead of orchestration through a single hub")
                        .build();

                patterns.add(pattern);
                log.info("ESB Misuse detected: service '{}' mediates {}% of traffic "
                                + "(called by {} services, calls {} services)",
                        ms.getName(), String.format("%.0f", mediatorRatio * 100),
                        uniqueCallers.size(), uniqueCallees.size());
            }
        }

        return patterns;
    }
}


