package com.msadetector.service.detection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msadetector.entity.CodeSmell;
import com.msadetector.entity.DetectedAntiPattern;
import com.msadetector.entity.Microservice;
import com.msadetector.entity.Project;
import com.msadetector.entity.ServiceDependency;
import com.msadetector.enums.AntiPatternType;
import com.msadetector.enums.Severity;
import com.msadetector.repository.CodeSmellRepository;
import com.msadetector.repository.ServiceDependencyRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.*;

/**
 * Detects "Wrong Cuts" — misplaced service boundaries where functionality
 * that should reside in one service is spread across multiple services,
 * or tightly-coupled functionality is incorrectly split.
 * <p>
 * Indicators:
 * <ul>
 *   <li>High number of Feature Envy code smells (classes that use methods
 *       from other classes more than their own — suggesting misplaced logic)</li>
 *   <li>Bidirectional (mutual) dependencies between services indicating
 *       that the split was wrong</li>
 * </ul>
 */
@Component
public class WrongCutsDetector extends BaseDetector {

    private static final String UNKNOWN = "unknown";

    private final CodeSmellRepository codeSmellRepository;
    private final ServiceDependencyRepository dependencyRepository;
    private final int minFeatureEnvySmells;

    public WrongCutsDetector(ObjectMapper objectMapper,
                              CodeSmellRepository codeSmellRepository,
                              ServiceDependencyRepository dependencyRepository,
                              @Value("${app.thresholds.wrong-cuts-min-feature-envy:3}") int minFeatureEnvySmells) {
        super(objectMapper);
        this.codeSmellRepository = codeSmellRepository;
        this.dependencyRepository = dependencyRepository;
        this.minFeatureEnvySmells = minFeatureEnvySmells;
    }

    @Override
    public List<DetectedAntiPattern> detect(Project project, List<Microservice> microservices) {
        List<DetectedAntiPattern> patterns = new ArrayList<>();
        Path projectRoot = Path.of(project.getLocalPath());

        for (Microservice ms : microservices) {
            List<CodeSmell> featureEnvySmells = codeSmellRepository.findByMicroservice(ms).stream()
                    .filter(smell -> "Feature Envy".equalsIgnoreCase(smell.getSmellType()))
                    .toList();

            if (featureEnvySmells.size() >= minFeatureEnvySmells) {
                List<Map<String, Object>> evidenceJson = featureEnvySmells.stream()
                        .map(smell -> Map.<String, Object>of(
                                "class", Optional.ofNullable(smell.getClassName()).orElse(UNKNOWN),
                                "method", Optional.ofNullable(smell.getMethodName()).orElse(UNKNOWN),
                                "file", Optional.ofNullable(smell.getFilePath()).orElse(UNKNOWN)
                        ))
                        .toList();

                List<Map<String, Object>> snippets = featureEnvySmells.stream()
                        .limit(5)
                        .map(smell -> resolveSmellSnippet(smell, projectRoot, ms))
                        .toList();

                DetectedAntiPattern pattern = DetectedAntiPattern.builder()
                        .patternType(AntiPatternType.WRONG_CUTS)
                        .severity(Severity.HIGH)
                        .description(String.format(
                                "Service '%s' has %d Feature Envy smell(s), suggesting that some of its classes "
                                        + "use external functionality more than their own. "
                                        + "This indicates misplaced service boundaries.",
                                ms.getName(), featureEnvySmells.size()
                        ))
                        .affectedServicesJson(toJson(List.of(ms.getName())))
                        .primaryService(ms)
                        .evidenceJson(toJson(evidenceJson))
                        .codeSnippetsJson(snippetsToJson(snippets))
                        .remediation("Re-evaluate service boundaries. Move classes with Feature Envy "
                                + "to the service whose domain they access most frequently, "
                                + "or merge tightly-coupled services")
                        .build();

                patterns.add(pattern);
                log.info("Wrong Cuts detected in service {} ({} Feature Envy smells)",
                        ms.getName(), featureEnvySmells.size());
            }
        }

        List<ServiceDependency> allDeps = dependencyRepository.findByProjectWithServices(project);
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





