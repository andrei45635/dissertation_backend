package com.msadetector.service.detection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msadetector.entity.DetectedAntiPattern;
import com.msadetector.entity.Microservice;
import com.msadetector.entity.Project;
import com.msadetector.enums.AntiPatternType;
import com.msadetector.enums.Severity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Detects hardcoded endpoint URLs in Java source code that indicate
 * services are not using service discovery.
 */
@Component
public class HardcodedEndpointDetector extends BaseDetector {

    private static final Pattern URL_PATTERN = Pattern.compile(
            "(https?://[\\w.\\-:]+[/\\w.\\-?=&#%]*)"
    );

    private final String hardcodedEndpointPatterns;

    public HardcodedEndpointDetector(ObjectMapper objectMapper,
                                      @Value("${app.thresholds.hardcoded-endpoint-patterns:http://,https://,localhost:,127.0.0.1}") String hardcodedEndpointPatterns) {
        super(objectMapper);
        this.hardcodedEndpointPatterns = hardcodedEndpointPatterns;
    }

    @Override
    public List<DetectedAntiPattern> detect(Project project, List<Microservice> microservices) {
        List<DetectedAntiPattern> patterns = new ArrayList<>();
        Path projectRoot = Path.of(project.getLocalPath());
        String[] urlPatterns = hardcodedEndpointPatterns.split(",");

        for (Microservice ms : microservices) {
            Path servicePath = projectRoot.resolve(ms.getRelativePath());
            Path srcDir = servicePath.resolve("src/main/java");
            if (!Files.exists(srcDir)) continue;

            List<HardcodedUrlEvidence> evidenceList = scanForHardcodedUrls(srcDir, servicePath, urlPatterns);

            if (!evidenceList.isEmpty()) {
                List<Map<String, Object>> evidenceJson = evidenceList.stream()
                        .map(e -> Map.<String, Object>of(
                                "file", e.file(),
                                "line", e.lineNumber(),
                                "code", truncate(e.code(), 200),
                                "url", e.url()
                        ))
                        .toList();

                // Build code snippets — read actual source with surrounding context
                List<Map<String, Object>> snippets = evidenceList.stream()
                        .limit(5) // cap at 5 snippets to avoid huge payloads
                        .map(e -> readSnippet(servicePath.resolve(e.file()), e.lineNumber()))
                        .toList();

                DetectedAntiPattern pattern = DetectedAntiPattern.builder()
                        .patternType(AntiPatternType.HARDCODED_ENDPOINTS)
                        .severity(Severity.MEDIUM)
                        .description(String.format(
                                "Service '%s' has %d hardcoded endpoint URL(s). "
                                        + "Hardcoded URLs prevent dynamic service discovery and complicate deployment.",
                                ms.getName(), evidenceList.size()
                        ))
                        .affectedServicesJson(toJson(List.of(ms.getName())))
                        .primaryService(ms)
                        .evidenceJson(toJson(evidenceJson))
                        .codeSnippetsJson(snippetsToJson(snippets))
                        .remediation("Use service discovery (e.g., Eureka, Consul) or externalize URLs to configuration")
                        .build();

                patterns.add(pattern);
                log.info("Hardcoded endpoints detected in service {}: {} occurrences", ms.getName(), evidenceList.size());
            }
        }

        return patterns;
    }

    private List<HardcodedUrlEvidence> scanForHardcodedUrls(Path srcDir, Path servicePath, String[] urlPatterns) {
        List<HardcodedUrlEvidence> evidenceList = new ArrayList<>();

        try (Stream<Path> javaFiles = Files.walk(srcDir).filter(p -> p.toString().endsWith(".java"))) {
            for (Path javaFile : javaFiles.toList()) {
                List<String> lines = Files.readAllLines(javaFile);
                String relativePath = servicePath.relativize(javaFile).toString();

                if (relativePath.contains("test") || relativePath.contains("Test")) {
                    continue;
                }

                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i).trim();

                    if (line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")
                            || line.startsWith("import ") || line.startsWith("package ")) {
                        continue;
                    }

                    for (String urlPattern : urlPatterns) {
                        if (line.contains("\"" + urlPattern.trim()) || line.contains("'" + urlPattern.trim())) {
                            Matcher matcher = URL_PATTERN.matcher(line);
                            String url = matcher.find() ? matcher.group(1) : urlPattern.trim();
                            evidenceList.add(new HardcodedUrlEvidence(relativePath, i + 1, line, url));
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Failed to scan for hardcoded endpoints: {}", e.getMessage());
        }

        return evidenceList;
    }

    private record HardcodedUrlEvidence(String file, int lineNumber, String code, String url) {}
}

