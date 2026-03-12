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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Detects chatty services — service pairs with an excessive number
 * of fine-grained calls that increase latency and coupling.
 */
@Component
public class ChattyServiceDetector extends BaseDetector {

    private final ServiceDependencyRepository dependencyRepository;
    private final int minCalls;

    public ChattyServiceDetector(ObjectMapper objectMapper,
                                  ServiceDependencyRepository dependencyRepository,
                                  @Value("${app.thresholds.chatty-service-min-calls:5}") int minCalls) {
        super(objectMapper);
        this.dependencyRepository = dependencyRepository;
        this.minCalls = minCalls;
    }

    @Override
    public List<DetectedAntiPattern> detect(Project project, List<Microservice> microservices) {
        List<DetectedAntiPattern> patterns = new ArrayList<>();
        Path projectRoot = Path.of(project.getLocalPath());

        List<ServiceDependency> chattyDeps = dependencyRepository.findChattyDependencies(project, minCalls);

        for (ServiceDependency dep : chattyDeps) {
            String sourceName = dep.getSourceService().getName();
            String targetName = dep.getTargetService().getName();

            // Build code snippet from the dependency evidence
            List<Map<String, Object>> snippets = new ArrayList<>();
            if (dep.getEvidenceFile() != null && dep.getEvidenceLine() != null && dep.getEvidenceLine() > 0) {
                Map<String, Object> snippet = readSnippet(
                        projectRoot.resolve(dep.getEvidenceFile()), dep.getEvidenceLine());
                if (snippet != null) snippets.add(snippet);
            } else if (dep.getEvidenceCode() != null) {
                Map<String, Object> snippet = buildSnippet(
                        dep.getEvidenceFile(), dep.getEvidenceLine() != null ? dep.getEvidenceLine() : 0,
                        dep.getEvidenceCode());
                if (snippet != null) snippets.add(snippet);
            }

            DetectedAntiPattern pattern = DetectedAntiPattern.builder()
                    .patternType(AntiPatternType.CHATTY_SERVICE)
                    .severity(Severity.HIGH)
                    .description(String.format(
                            "Excessive communication between '%s' and '%s' (%d calls detected). "
                                    + "Fine-grained calls increase latency and coupling.",
                            sourceName, targetName, dep.getCallCount()
                    ))
                    .affectedServicesJson(toJson(List.of(sourceName, targetName)))
                    .primaryService(dep.getSourceService())
                    .callCount(dep.getCallCount())
                    .codeSnippetsJson(snippetsToJson(snippets))
                    .remediation("Consider aggregating multiple fine-grained calls into a single coarse-grained API, "
                            + "or use asynchronous messaging for non-critical communication")
                    .build();

            patterns.add(pattern);
            log.info("Chatty service detected: {} -> {} ({} calls)", sourceName, targetName, dep.getCallCount());
        }

        return patterns;
    }
}

