package com.msadetector.service.detection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msadetector.entity.CodeSmell;
import com.msadetector.entity.DetectedAntiPattern;
import com.msadetector.entity.Microservice;
import com.msadetector.entity.Project;
import com.msadetector.enums.AntiPatternType;
import com.msadetector.enums.Severity;
import com.msadetector.repository.CodeSmellRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Detects god services — services handling too many responsibilities,
 * identified by a high number of God Class code smells from DesigniteJava.
 */
@Component
public class GodServiceDetector extends BaseDetector {

    private final CodeSmellRepository codeSmellRepository;
    private final int minDomains;

    public GodServiceDetector(ObjectMapper objectMapper,
                               CodeSmellRepository codeSmellRepository,
                               @Value("${app.thresholds.god-service-min-domains:3}") int minDomains) {
        super(objectMapper);
        this.codeSmellRepository = codeSmellRepository;
        this.minDomains = minDomains;
    }

    @Override
    public List<DetectedAntiPattern> detect(Project project, List<Microservice> microservices) {
        List<DetectedAntiPattern> patterns = new ArrayList<>();
        Path projectRoot = Path.of(project.getLocalPath());

        for (Microservice ms : microservices) {
            List<CodeSmell> godClassSmells = codeSmellRepository.findByMicroservice(ms).stream()
                    .filter(smell -> "God Class".equalsIgnoreCase(smell.getSmellType()))
                    .toList();

            if (godClassSmells.size() >= minDomains) {
                // Collect code snippets from each God Class occurrence
                List<Map<String, Object>> snippets = godClassSmells.stream()
                        .map(smell -> {
                            if (smell.getFilePath() != null && smell.getLineNumber() != null) {
                                return readSnippet(projectRoot.resolve(smell.getFilePath()), smell.getLineNumber(), 5);
                            } else if (smell.getFilePath() != null) {
                                // No line number — show the class declaration (first lines)
                                return readSnippet(projectRoot.resolve(smell.getFilePath()), 1, 10);
                            }
                            return buildSnippet(smell.getClassName(), 0, smell.getDescription());
                        })
                        .toList();

                DetectedAntiPattern pattern = DetectedAntiPattern.builder()
                        .patternType(AntiPatternType.GOD_SERVICE)
                        .severity(Severity.HIGH)
                        .description(String.format(
                                "Service '%s' has %d God Class smells, indicating too many responsibilities",
                                ms.getName(), godClassSmells.size()
                        ))
                        .affectedServicesJson(toJson(List.of(ms.getName())))
                        .primaryService(ms)
                        .codeSnippetsJson(snippetsToJson(snippets))
                        .remediation("Consider decomposing into smaller, focused services")
                        .build();

                patterns.add(pattern);
            }
        }

        return patterns;
    }
}

