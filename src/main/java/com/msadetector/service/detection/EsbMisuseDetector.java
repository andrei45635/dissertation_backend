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
 * Uses three complementary signals:
 * <ol>
 *   <li><b>Connection-based:</b> a service both receives calls from and makes
 *       calls to a high proportion of other services</li>
 *   <li><b>Volume-based:</b> a service concentrates a disproportionate share
 *       of all dependency traffic</li>
 *   <li><b>Betweenness centrality:</b> a service lies on many shortest paths
 *       between other service pairs, indicating it acts as a communication
 *       bottleneck (Brandes' algorithm)</li>
 * </ol>
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
        Map<Long, Set<Long>> adjacency = new HashMap<>();
        for (Microservice ms : microservices) {
            serviceNames.put(ms.getId(), ms.getName());
            adjacency.put(ms.getId(), new HashSet<>());
        }
        for (ServiceDependency dep : allDeps) {
            adjacency.computeIfAbsent(dep.getSourceService().getId(), _ -> new HashSet<>())
                    .add(dep.getTargetService().getId());
        }

        Map<Long, Double> betweenness = computeBetweennessCentrality(adjacency);
        int n = microservices.size();
        double normFactor = (n - 1) * (n - 2);

        for (Microservice ms : microservices) {
            Long msId = ms.getId();

            boolean isLikelyGateway = GATEWAY_KEYWORDS.stream()
                    .anyMatch(kw -> ms.getName().toLowerCase().contains(kw));
            if (isLikelyGateway) {
                log.debug("Skipping service '{}' — likely an API gateway (not ESB misuse)", ms.getName());
                continue;
            }

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

            double rawBC = betweenness.getOrDefault(msId, 0.0);
            double normalizedBC = normFactor > 0 ? rawBC / normFactor : 0;

            boolean isMediatorByConnections = callerRatio >= mediatorThreshold && calleeRatio >= mediatorThreshold;
            boolean isMediatorByVolume = mediatorRatio >= mediatorThreshold;
            boolean isMediatorByCentrality = normalizedBC >= mediatorThreshold;

            if (isMediatorByConnections || isMediatorByVolume || isMediatorByCentrality) {
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
                                        + "Betweenness centrality: %.2f. "
                                        + "This creates a single point of failure and bottleneck.",
                                ms.getName(),
                                uniqueCallers.size(), callerRatio * 100,
                                uniqueCallees.size(), calleeRatio * 100,
                                totalThroughService, totalDependencies,
                                normalizedBC
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
                                "mediatorRatio", mediatorRatio,
                                "betweennessCentrality", normalizedBC
                        )))
                        .codeSnippetsJson(snippetsToJson(snippets))
                        .remediation("Eliminate the central mediator by having services communicate directly "
                                + "with each other using service discovery, or adopt choreography-based "
                                + "event-driven communication instead of orchestration through a single hub")
                        .build();

                patterns.add(pattern);
                log.info("ESB Misuse detected: service '{}' — callerRatio={}%, calleeRatio={}%, "
                                + "mediatorRatio={}%, betweennessCentrality={}",
                        ms.getName(),
                        String.format("%.0f", callerRatio * 100),
                        String.format("%.0f", calleeRatio * 100),
                        String.format("%.0f", mediatorRatio * 100),
                        String.format("%.2f", normalizedBC));
            }
        }

        return patterns;
    }

    /**
     * Computes betweenness centrality for all nodes in a directed unweighted
     * graph using Brandes' algorithm.
     * <p>
     * Betweenness centrality of a node v is the sum over all pairs (s,t) of
     * the fraction of shortest paths from s to t that pass through v:
     * <pre>
     *   BC(v) = Σ_{s≠v≠t} σ_st(v) / σ_st
     * </pre>
     * Time complexity: O(V × E) for unweighted graphs.
     *
     * @see <a href="https://doi.org/10.1080/0022250X.2001.9990249">
     *      Brandes, "A Faster Algorithm for Betweenness Centrality" (2001)</a>
     */
    private Map<Long, Double> computeBetweennessCentrality(Map<Long, Set<Long>> adjacency) {
        Map<Long, Double> centrality = new HashMap<>();
        for (Long v : adjacency.keySet()) {
            centrality.put(v, 0.0);
        }

        for (Long s : adjacency.keySet()) {
            Deque<Long> stack = new ArrayDeque<>();
            Map<Long, List<Long>> predecessors = new HashMap<>();
            Map<Long, Long> sigma = new HashMap<>();
            Map<Long, Integer> dist = new HashMap<>();

            for (Long v : adjacency.keySet()) {
                predecessors.put(v, new ArrayList<>());
                sigma.put(v, 0L);
                dist.put(v, -1);
            }

            sigma.put(s, 1L);
            dist.put(s, 0);

            Queue<Long> queue = new ArrayDeque<>();
            queue.add(s);

            while (!queue.isEmpty()) {
                Long v = queue.poll();
                stack.push(v);

                for (Long w : adjacency.getOrDefault(v, Set.of())) {
                    if (dist.get(w) < 0) {
                        queue.add(w);
                        dist.put(w, dist.get(v) + 1);
                    }
                    if (dist.get(w).equals(dist.get(v) + 1)) {
                        sigma.put(w, sigma.get(w) + sigma.get(v));
                        predecessors.get(w).add(v);
                    }
                }
            }

            Map<Long, Double> delta = new HashMap<>();
            for (Long v : adjacency.keySet()) {
                delta.put(v, 0.0);
            }

            while (!stack.isEmpty()) {
                Long w = stack.pop();
                for (Long v : predecessors.get(w)) {
                    double fraction = (double) sigma.get(v) / sigma.get(w) * (1.0 + delta.get(w));
                    delta.put(v, delta.get(v) + fraction);
                }
                if (!w.equals(s)) {
                    centrality.put(w, centrality.get(w) + delta.get(w));
                }
            }
        }

        return centrality;
    }
}


