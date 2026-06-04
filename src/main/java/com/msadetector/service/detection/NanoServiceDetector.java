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
import java.util.stream.Stream;

/**
 * Detects nano services — services that are too small to justify their
 * operational overhead. Uses configurable LOC and endpoint thresholds.
 */
@Component
public class NanoServiceDetector extends BaseDetector {

    private final int maxLoc;
    private final int maxEndpoints;

    public NanoServiceDetector(ObjectMapper objectMapper,
                                @Value("${app.thresholds.nano-service-max-loc:500}") int maxLoc,
                                @Value("${app.thresholds.nano-service-max-endpoints:2}") int maxEndpoints) {
        super(objectMapper);
        this.maxLoc = maxLoc;
        this.maxEndpoints = maxEndpoints;
    }

    @Override
    public List<DetectedAntiPattern> detect(Project project, List<Microservice> microservices) {
        List<DetectedAntiPattern> patterns = new ArrayList<>();
        Path projectRoot = Path.of(project.getLocalPath());

        for (Microservice ms : microservices) {
            if (ms.getLinesOfCode() < maxLoc && ms.getNumberOfEndpoints() <= maxEndpoints) {
                List<Map<String, Object>> snippets = new ArrayList<>();
                Path serviceSrc = projectRoot.resolve(ms.getRelativePath()).resolve("src/main/java");
                Path mainClass = findMainClass(serviceSrc);
                if (mainClass != null) {
                    Map<String, Object> snippet = readSnippet(mainClass, 1, 15);
                    if (snippet != null) snippets.add(snippet);
                }

                DetectedAntiPattern pattern = DetectedAntiPattern.builder()
                        .patternType(AntiPatternType.NANO_SERVICE)
                        .severity(Severity.MEDIUM)
                        .description(String.format(
                                "Service '%s' is too small (%d LOC, %d endpoints) to justify operational overhead",
                                ms.getName(), ms.getLinesOfCode(), ms.getNumberOfEndpoints()
                        ))
                        .affectedServicesJson(toJson(List.of(ms.getName())))
                        .primaryService(ms)
                        .codeSnippetsJson(snippetsToJson(snippets))
                        .remediation("Consider merging with a related service")
                        .build();

                patterns.add(pattern);
            }
        }

        return patterns;
    }

    /**
     * Finds the Spring Boot main application class or falls back to
     * the first Java file in the source directory.
     */
    private Path findMainClass(Path srcDir) {
        if (srcDir == null || !Files.exists(srcDir)) return null;
        try (Stream<Path> files = Files.walk(srcDir).filter(p -> p.toString().endsWith(".java"))) {
            for (Path file : files.toList()) {
                String content = Files.readString(file, java.nio.charset.StandardCharsets.ISO_8859_1);
                if (content.contains("@SpringBootApplication") || content.contains("public static void main")) {
                    return file;
                }
            }
        } catch (IOException e) {
            log.debug("Could not scan for main class: {}", e.getMessage());
        }

        try (Stream<Path> files = Files.walk(srcDir).filter(p -> p.toString().endsWith(".java"))) {
            return files.findFirst().orElse(null);
        } catch (IOException e) {
            return null;
        }
    }
}

