package com.msadetector.service.detection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msadetector.entity.DetectedAntiPattern;
import com.msadetector.entity.Microservice;
import com.msadetector.entity.Project;
import com.msadetector.enums.AntiPatternType;
import com.msadetector.enums.Severity;
import com.msadetector.enums.SourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GodServiceDetectorTest {

    @TempDir
    Path tempDir;

    private final GodServiceDetector detector = new GodServiceDetector(
            new ObjectMapper(), 25, 30, 1000, 20, 12, 0.5, 3);

    private Project createProject() {
        Project project = new Project();
        project.setName("test-project");
        project.setSourceType(SourceType.UPLOAD);
        project.setLocalPath(tempDir.toString());
        return project;
    }

    private Microservice createMs(String name) {
        Microservice ms = new Microservice();
        ms.setName(name);
        ms.setRelativePath(name);
        return ms;
    }

    /** Writes a Java source file under {@code <service>/src/main/java}. */
    private void writeSource(String service, String className, String code) throws IOException {
        Path dir = tempDir.resolve(service).resolve("src/main/java");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(className + ".java"), code);
    }

    /**
     * Builds a class that trips three God Class metrics at once: it declares many
     * fields and many public methods, and each method touches a different field, so
     * the class has near-zero tight class cohesion. The methods are not accessors,
     * so the class is not mistaken for a data holder.
     */
    private String godClassSource(String className, int members) {
        StringBuilder sb = new StringBuilder();
        sb.append("public class ").append(className).append(" {\n");
        for (int i = 0; i < members; i++) {
            sb.append("    private int field").append(i).append(";\n");
        }
        for (int i = 0; i < members; i++) {
            sb.append("    public void process").append(i).append("() { field")
              .append(i).append(" = field").append(i).append(" + 1; }\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    @Test
    void detect_godClass_flagsService() throws IOException {
        Project project = createProject();
        Microservice ms = createMs("big-svc");
        writeSource("big-svc", "OrderManager", godClassSource("OrderManager", 30));

        List<DetectedAntiPattern> results = detector.detect(project, List.of(ms));

        assertEquals(1, results.size());
        DetectedAntiPattern pattern = results.getFirst();
        assertEquals(AntiPatternType.GOD_SERVICE, pattern.getPatternType());
        assertEquals(Severity.HIGH, pattern.getSeverity());
        assertTrue(pattern.getDescription().contains("big-svc"));
        assertTrue(pattern.getDescription().contains("OrderManager"));
    }

    @Test
    void detect_smallClass_returnsEmpty() throws IOException {
        Project project = createProject();
        Microservice ms = createMs("clean-svc");
        writeSource("clean-svc", "Calculator",
                "public class Calculator {\n"
              + "    private int a;\n"
              + "    public int add(int b) { return a + b; }\n"
              + "}\n");

        List<DetectedAntiPattern> results = detector.detect(project, List.of(ms));

        assertTrue(results.isEmpty());
    }

    @Test
    void detect_dataClassExcluded_notFlagged() throws IOException {
        Project project = createProject();
        Microservice ms = createMs("dto-svc");

        writeSource("dto-svc", "CustomerDto", godClassSource("CustomerDto", 30));

        List<DetectedAntiPattern> results = detector.detect(project, List.of(ms));

        assertTrue(results.isEmpty());
    }

    @Test
    void detect_noSourceDirectory_returnsEmpty() {
        Project project = createProject();
        Microservice ms = createMs("missing-svc");

        List<DetectedAntiPattern> results = detector.detect(project, List.of(ms));

        assertTrue(results.isEmpty());
    }

    @Test
    void detect_multipleServices_onlyFlagsGodOnes() throws IOException {
        Project project = createProject();
        Microservice clean = createMs("clean-svc");
        Microservice god = createMs("god-svc");
        writeSource("clean-svc", "Calculator",
                "public class Calculator {\n"
              + "    private int a;\n"
              + "    public int add(int b) { return a + b; }\n"
              + "}\n");
        writeSource("god-svc", "OrderManager", godClassSource("OrderManager", 30));

        List<DetectedAntiPattern> results = detector.detect(project, List.of(clean, god));

        assertEquals(1, results.size());
        assertTrue(results.getFirst().getDescription().contains("god-svc"));
    }
}
