package com.msadetector.service.detection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msadetector.entity.DetectedAntiPattern;
import com.msadetector.entity.Microservice;
import com.msadetector.entity.Project;
import com.msadetector.enums.AntiPatternType;
import com.msadetector.enums.Severity;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Detects shared database anti-pattern by comparing datasource URLs
 * across microservices. Multiple services pointing to the same database
 * indicates tight data-level coupling.
 */
@Component
public class SharedDatabaseDetector extends BaseDetector {

    public SharedDatabaseDetector(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public List<DetectedAntiPattern> detect(Project project, List<Microservice> microservices) {
        List<DetectedAntiPattern> patterns = new ArrayList<>();
        Path projectRoot = Path.of(project.getLocalPath());

        Map<String, List<Microservice>> byDatasource = microservices.stream()
                .filter(ms -> ms.getDatasourceUrl() != null && !ms.getDatasourceUrl().isBlank())
                .collect(Collectors.groupingBy(Microservice::getDatasourceUrl));

        for (Map.Entry<String, List<Microservice>> entry : byDatasource.entrySet()) {
            if (entry.getValue().size() > 1) {
                List<String> serviceNames = entry.getValue().stream()
                        .map(Microservice::getName)
                        .toList();

                // Read the datasource config snippet from each sharing service
                List<Map<String, Object>> snippets = entry.getValue().stream()
                        .map(ms -> readDatasourceSnippet(projectRoot, ms))
                        .toList();

                DetectedAntiPattern pattern = DetectedAntiPattern.builder()
                        .patternType(AntiPatternType.SHARED_DATABASE)
                        .severity(Severity.HIGH)
                        .description("Multiple services share the same database: " + entry.getKey())
                        .affectedServicesJson(toJson(serviceNames))
                        .sharedDatabaseUrl(entry.getKey())
                        .codeSnippetsJson(snippetsToJson(snippets))
                        .remediation("Consider database-per-service pattern with data synchronization via events")
                        .build();

                patterns.add(pattern);
            }
        }

        return patterns;
    }

    /**
     * Finds and reads the datasource configuration line from a service's
     * application.yml/application.properties.
     */
    private Map<String, Object> readDatasourceSnippet(Path projectRoot, Microservice ms) {
        Path servicePath = projectRoot.resolve(ms.getRelativePath()).resolve("src/main/resources");

        for (String filename : List.of("application.yml", "application.yaml", "application.properties")) {
            Path configFile = servicePath.resolve(filename);
            if (!Files.exists(configFile)) continue;

            try {
                List<String> lines = Files.readAllLines(configFile);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i).toLowerCase();
                    if (line.contains("datasource") || line.contains("spring.datasource.url")) {
                        return readSnippet(configFile, i + 1, 3);
                    }
                }
                // If datasource keyword not found, show beginning of config
                return readSnippet(configFile, 1, 10);
            } catch (IOException e) {
                log.debug("Could not read config for {}: {}", ms.getName(), e.getMessage());
            }
        }
        return null;
    }
}

