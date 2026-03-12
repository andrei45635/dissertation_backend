package com.msadetector.service.detection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msadetector.entity.DetectedAntiPattern;
import com.msadetector.entity.Microservice;
import com.msadetector.entity.Project;
import com.msadetector.entity.ServiceDependency;
import com.msadetector.enums.AntiPatternType;
import com.msadetector.enums.Severity;
import com.msadetector.repository.ServiceDependencyRepository;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.*;

/**
 * Detects cyclic dependencies between microservices using Tarjan's
 * Strongly Connected Components algorithm. Any SCC with more than
 * one node represents a dependency cycle.
 */
@Component
public class CyclicDependencyDetector extends BaseDetector {

    private final ServiceDependencyRepository dependencyRepository;

    public CyclicDependencyDetector(ObjectMapper objectMapper,
                                     ServiceDependencyRepository dependencyRepository) {
        super(objectMapper);
        this.dependencyRepository = dependencyRepository;
    }

    @Override
    public List<DetectedAntiPattern> detect(Project project, List<Microservice> microservices) {
        List<DetectedAntiPattern> patterns = new ArrayList<>();
        List<ServiceDependency> dependencies = dependencyRepository.findByProjectWithServices(project);
        Path projectRoot = Path.of(project.getLocalPath());

        // Build adjacency list
        Map<Long, Set<Long>> adjacency = new HashMap<>();
        Map<Long, String> serviceNames = new HashMap<>();

        // Index dependencies for quick lookup of evidence
        Map<String, ServiceDependency> depByEdge = new HashMap<>();

        for (ServiceDependency dep : dependencies) {
            Long sourceId = dep.getSourceService().getId();
            Long targetId = dep.getTargetService().getId();
            adjacency.computeIfAbsent(sourceId, _ -> new HashSet<>()).add(targetId);
            adjacency.computeIfAbsent(targetId, _ -> new HashSet<>());
            serviceNames.put(sourceId, dep.getSourceService().getName());
            serviceNames.put(targetId, dep.getTargetService().getName());
            depByEdge.put(sourceId + "->" + targetId, dep);
        }

        List<List<Long>> sccs = tarjanSCC(adjacency);

        for (List<Long> scc : sccs) {
            if (scc.size() > 1) {
                List<String> cycleServiceNames = scc.stream()
                        .map(serviceNames::get)
                        .filter(Objects::nonNull)
                        .toList();

                String cycleDescription = String.join(" -> ", cycleServiceNames)
                        + " -> " + cycleServiceNames.getFirst();

                // Collect code snippets for each edge in the cycle
                List<Map<String, Object>> snippets = new ArrayList<>();
                for (int i = 0; i < scc.size() && snippets.size() < 5; i++) {
                    Long from = scc.get(i);
                    Long to = scc.get((i + 1) % scc.size());
                    ServiceDependency dep = depByEdge.get(from + "->" + to);
                    if (dep != null && dep.getEvidenceFile() != null) {
                        Map<String, Object> snippet;
                        if (dep.getEvidenceLine() != null && dep.getEvidenceLine() > 0) {
                            snippet = readSnippet(projectRoot.resolve(dep.getEvidenceFile()), dep.getEvidenceLine());
                        } else {
                            snippet = buildSnippet(dep.getEvidenceFile(),
                                    dep.getEvidenceLine() != null ? dep.getEvidenceLine() : 0,
                                    dep.getEvidenceCode());
                        }
                        if (snippet != null) snippets.add(snippet);
                    }
                }

                DetectedAntiPattern pattern = DetectedAntiPattern.builder()
                        .patternType(AntiPatternType.CYCLIC_DEPENDENCY)
                        .severity(Severity.CRITICAL)
                        .description("Cyclic dependency detected: " + cycleDescription)
                        .affectedServicesJson(toJson(cycleServiceNames))
                        .cycleLength(scc.size())
                        .codeSnippetsJson(snippetsToJson(snippets))
                        .remediation("Break the cycle by introducing an event-driven architecture, "
                                + "extracting a shared library, or refactoring the communication direction")
                        .build();

                patterns.add(pattern);
                log.info("Detected cyclic dependency: {}", cycleDescription);
            }
        }

        return patterns;
    }

    private List<List<Long>> tarjanSCC(Map<Long, Set<Long>> adjacency) {
        List<List<Long>> result = new ArrayList<>();
        Map<Long, Integer> index = new HashMap<>();
        Map<Long, Integer> lowlink = new HashMap<>();
        Set<Long> onStack = new HashSet<>();
        Deque<Long> stack = new ArrayDeque<>();
        int[] counter = {0};

        for (Long node : adjacency.keySet()) {
            if (!index.containsKey(node)) {
                tarjanDFS(node, adjacency, index, lowlink, onStack, stack, counter, result);
            }
        }

        return result;
    }

    private void tarjanDFS(Long node, Map<Long, Set<Long>> adjacency,
                           Map<Long, Integer> index, Map<Long, Integer> lowlink,
                           Set<Long> onStack, Deque<Long> stack,
                           int[] counter, List<List<Long>> result) {
        index.put(node, counter[0]);
        lowlink.put(node, counter[0]);
        counter[0]++;
        stack.push(node);
        onStack.add(node);

        for (Long neighbor : adjacency.getOrDefault(node, Set.of())) {
            if (!index.containsKey(neighbor)) {
                tarjanDFS(neighbor, adjacency, index, lowlink, onStack, stack, counter, result);
                lowlink.put(node, Math.min(lowlink.get(node), lowlink.get(neighbor)));
            } else if (onStack.contains(neighbor)) {
                lowlink.put(node, Math.min(lowlink.get(node), index.get(neighbor)));
            }
        }

        if (lowlink.get(node).equals(index.get(node))) {
            List<Long> scc = new ArrayList<>();
            Long w;
            do {
                w = stack.pop();
                onStack.remove(w);
                scc.add(w);
            } while (!w.equals(node));
            result.add(scc);
        }
    }
}

