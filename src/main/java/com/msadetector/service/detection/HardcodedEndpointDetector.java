package com.msadetector.service.detection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msadetector.entity.AnalysisJob;
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
            "(?i)(https?://[\\w.\\-:]+[/\\w.\\-?=&#%]*)"
                    + "|((?:localhost|127\\.0\\.0\\.1|\\d{1,3}(?:\\.\\d{1,3}){3})"
                    + ":\\d{2,5}(?:[/\\w.\\-?=&#%]*)?)"
    );

    public HardcodedEndpointDetector(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public List<DetectedAntiPattern> detect(Project project, List<Microservice> microservices, AnalysisJob job) {
        List<DetectedAntiPattern> patterns = new ArrayList<>();
        Path projectRoot = Path.of(project.getLocalPath());

        for (Microservice ms : microservices) {
            Path servicePath = projectRoot.resolve(ms.getRelativePath());
            Path srcDir = servicePath.resolve("src/main/java");
            if (!Files.exists(srcDir)) continue;

            List<HardcodedUrlEvidence> evidenceList = scanForHardcodedUrls(srcDir, servicePath);

            if (!evidenceList.isEmpty()) {
                List<Map<String, Object>> evidenceJson = evidenceList.stream()
                        .map(e -> Map.<String, Object>of(
                                "file", e.file(),
                                "line", e.lineNumber(),
                                "code", truncate(e.code(), 200),
                                "url", e.url()
                        ))
                        .toList();

                List<Map<String, Object>> snippets = evidenceList.stream()
                        .limit(5)
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

    private List<HardcodedUrlEvidence> scanForHardcodedUrls(Path srcDir, Path servicePath) {
        List<HardcodedUrlEvidence> evidenceList = new ArrayList<>();

        try (Stream<Path> javaFiles = Files.walk(srcDir).filter(p -> p.toString().endsWith(".java"))) {
            for (Path javaFile : javaFiles.toList()) {
                List<String> lines = Files.readAllLines(javaFile, java.nio.charset.StandardCharsets.ISO_8859_1);
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

                    Matcher matcher = URL_PATTERN.matcher(line);
                    while (matcher.find()) {
                        String url = matcher.group();
                        if (isInsideStringLiteral(line, matcher.start())) {
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

    private boolean isInsideStringLiteral(String line, int matchStart) {
        boolean inSingleQuotedLiteral = false;
        boolean inDoubleQuotedLiteral = false;
        boolean escaped = false;

        for (int i = 0; i < matchStart; i++) {
            char ch = line.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (ch == '\\') {
                escaped = true;
                continue;
            }
            if (ch == '"' && !inSingleQuotedLiteral) {
                inDoubleQuotedLiteral = !inDoubleQuotedLiteral;
            } else if (ch == '\'' && !inDoubleQuotedLiteral) {
                inSingleQuotedLiteral = !inSingleQuotedLiteral;
            }
        }

        return inSingleQuotedLiteral || inDoubleQuotedLiteral;
    }

    private record HardcodedUrlEvidence(String file, int lineNumber, String code, String url) {}
}
