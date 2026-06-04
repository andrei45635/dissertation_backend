package com.msadetector.service.detection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msadetector.entity.AnalysisJob;
import com.msadetector.entity.DetectedAntiPattern;
import com.msadetector.entity.Endpoint;
import com.msadetector.entity.Microservice;
import com.msadetector.entity.Project;
import com.msadetector.enums.AntiPatternType;
import com.msadetector.enums.Severity;
import com.msadetector.repository.EndpointRepository;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Detects absence of API versioning strategy by checking whether
 * endpoints include versioning patterns (e.g., /v1/, /v2/).
 */
@Component
public class ApiVersioningDetector extends BaseDetector {

    private final EndpointRepository endpointRepository;

    public ApiVersioningDetector(ObjectMapper objectMapper,
                                  EndpointRepository endpointRepository) {
        super(objectMapper);
        this.endpointRepository = endpointRepository;
    }

    @Override
    public List<DetectedAntiPattern> detect(Project project, List<Microservice> microservices, AnalysisJob job) {
        List<DetectedAntiPattern> patterns = new ArrayList<>();
        Path projectRoot = Path.of(project.getLocalPath());

        for (Microservice ms : microservices) {
            List<Endpoint> endpoints = endpointRepository.findByMicroservice(ms);
            if (endpoints.isEmpty()) continue;

            long versionedCount = endpoints.stream().filter(this::hasVersioningStrategy).count();
            long unversionedCount = endpoints.size() - versionedCount;

            if (versionedCount == 0) {
                List<String> unversionedPaths = endpoints.stream()
                        .map(Endpoint::getPath)
                        .toList();

                Path serviceSrc = projectRoot.resolve(ms.getRelativePath()).resolve("src/main/java");
                List<Map<String, Object>> snippets = endpoints.stream()
                        .map(Endpoint::getControllerClass)
                        .distinct()
                        .limit(3)
                        .map(controllerClass -> findControllerSnippet(serviceSrc, controllerClass))
                        .toList();

                DetectedAntiPattern pattern = DetectedAntiPattern.builder()
                        .patternType(AntiPatternType.API_VERSIONING_ABSENCE)
                        .severity(Severity.MEDIUM)
                        .description(String.format(
                                "Service '%s' has %d endpoint(s) with no API versioning strategy. "
                                        + "Without versioning, breaking changes cannot be managed gracefully.",
                                ms.getName(), unversionedCount
                        ))
                        .affectedServicesJson(toJson(List.of(ms.getName())))
                        .primaryService(ms)
                        .detailsJson(toJson(Map.of(
                                "totalEndpoints", endpoints.size(),
                                "versionedEndpoints", versionedCount,
                                "unversionedEndpoints", unversionedCount,
                                "unversionedPaths", unversionedPaths
                        )))
                        .codeSnippetsJson(snippetsToJson(snippets))
                        .remediation("Adopt an API versioning strategy such as URL path versioning (/v1/resource), "
                                + "header versioning (e.g., X-API-Version), or query parameter versioning")
                        .build();

                patterns.add(pattern);
            }
        }

        return patterns;
    }

    private boolean hasVersioningStrategy(Endpoint endpoint) {
        return endpoint.isHasVersioning()
                || (endpoint.getApiVersion() != null && !endpoint.getApiVersion().isBlank());
    }

    /**
     * Finds a controller Java file by its qualified class name and returns
     * a snippet showing the class declaration and @RequestMapping.
     */
    private Map<String, Object> findControllerSnippet(Path srcDir, String qualifiedClassName) {
        if (qualifiedClassName == null || srcDir == null || !Files.exists(srcDir)) return null;

        String relativePath = qualifiedClassName.replace('.', '/') + ".java";
        Path controllerFile = srcDir.resolve(relativePath);

        if (Files.exists(controllerFile)) {
            try {
                List<String> lines = Files.readAllLines(controllerFile, java.nio.charset.StandardCharsets.ISO_8859_1);

                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i).trim();
                    if (line.contains("@RestController") || line.contains("@Controller")
                            || line.contains("@RequestMapping")) {
                        return readSnippet(controllerFile, i + 1, 5);
                    }
                }

                return readSnippet(controllerFile, 1, 10);
            } catch (IOException e) {
                log.debug("Could not read controller file {}: {}", controllerFile, e.getMessage());
            }
        }
        return null;
    }
}
