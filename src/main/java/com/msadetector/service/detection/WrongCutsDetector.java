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
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.*;

/**
 * Detects "Wrong Cuts" — misplaced service boundaries where functionality
 * that should reside in one service is spread across multiple services,
 * or tightly-coupled functionality is incorrectly split.
 * <p>
 * Indicator: bidirectional (mutual) dependencies between services, which
 * indicate that two services are too tightly coupled and may have been split
 * incorrectly.
 */
@Component
public class WrongCutsDetector extends BaseDetector {

    private final ServiceDependencyRepository dependencyRepository;

    public WrongCutsDetector(ObjectMapper objectMapper,
                              ServiceDependencyRepository dependencyRepository) {
        super(objectMapper);
        this.dependencyRepository = dependencyRepository;
    }

    @Override
    public List<DetectedAntiPattern> detect(Project project, List<Microservice> microservices, AnalysisJob job) {
        List<DetectedAntiPattern> patterns = new ArrayList<>();
        Path projectRoot = Path.of(project.getLocalPath());

        List<ServiceDependency> allDeps = job != null
                ? dependencyRepository.findByAnalysisJobWithServices(job)
                : dependencyRepository.findByProjectWithServices(project);
        Set<String> edgeSet = new HashSet<>();
        Map<String, ServiceDependency> depByEdge = new HashMap<>();

        for (ServiceDependency dep : allDeps) {
            String key = dep.getSourceService().getId() + "->" + dep.getTargetService().getId();
            edgeSet.add(key);
            depByEdge.put(key, dep);
        }

        Set<String> reportedPairs = new HashSet<>();
        for (ServiceDependency dep : allDeps) {
            Long srcId = dep.getSourceService().getId();
            Long tgtId = dep.getTargetService().getId();
            String forwardKey = srcId + "->" + tgtId;
            String reverseKey = tgtId + "->" + srcId;
            String pairKey = Math.min(srcId, tgtId) + "<->" + Math.max(srcId, tgtId);

            if (edgeSet.contains(reverseKey) && !reportedPairs.contains(pairKey)) {
                reportedPairs.add(pairKey);

                String srcName = dep.getSourceService().getName();
                String tgtName = dep.getTargetService().getName();

                List<Map<String, Object>> biDirSnippets = new ArrayList<>();
                for (String edgeKey : List.of(forwardKey, reverseKey)) {
                    ServiceDependency edgeDep = depByEdge.get(edgeKey);
                    if (edgeDep != null && edgeDep.getEvidenceFile() != null) {
                        Map<String, Object> snippet;
                        if (edgeDep.getEvidenceLine() != null && edgeDep.getEvidenceLine() > 0) {
                            snippet = readSnippet(projectRoot.resolve(edgeDep.getEvidenceFile()), edgeDep.getEvidenceLine());
                        } else {
                            snippet = buildSnippet(edgeDep.getEvidenceFile(), 0, edgeDep.getEvidenceCode());
                        }
                        if (snippet != null) biDirSnippets.add(snippet);
                    }
                }

                DetectedAntiPattern pattern = DetectedAntiPattern.builder()
                        .patternType(AntiPatternType.WRONG_CUTS)
                        .severity(Severity.HIGH)
                        .description(String.format(
                                "Bidirectional dependency between '%s' and '%s'. "
                                        + "Mutual dependencies suggest these services are too tightly coupled "
                                        + "and may have been incorrectly split.",
                                srcName, tgtName
                        ))
                        .affectedServicesJson(toJson(List.of(srcName, tgtName)))
                        .detailsJson(toJson(Map.of(
                                "service1", srcName,
                                "service2", tgtName,
                                "direction", "bidirectional"
                        )))
                        .codeSnippetsJson(snippetsToJson(biDirSnippets))
                        .remediation("Consider merging these services or extracting the shared domain "
                                + "into a separate service to eliminate the bidirectional dependency")
                        .build();

                patterns.add(pattern);
                log.info("Wrong Cuts: bidirectional dependency between {} and {}", srcName, tgtName);
            }
        }

        return patterns;
    }
}





