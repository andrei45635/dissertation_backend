package com.msadetector.service.detection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msadetector.entity.DetectedAntiPattern;
import com.msadetector.entity.Microservice;
import com.msadetector.entity.Project;
import com.msadetector.enums.AntiPatternType;
import com.msadetector.enums.Severity;
import com.msadetector.enums.SourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NanoServiceDetectorTest {

    private NanoServiceDetector detector;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // maxLoc=500, maxEndpoints=2
        detector = new NanoServiceDetector(new ObjectMapper(), 500, 2);
    }

    private Project createProject() {
        Project project = new Project();
        project.setName("test-project");
        project.setSourceType(SourceType.UPLOAD);
        project.setLocalPath(tempDir.toString());
        return project;
    }

    private Microservice createMs(String name, int loc, int endpoints) {
        Microservice ms = new Microservice();
        ms.setName(name);
        ms.setRelativePath(name);
        ms.setLinesOfCode(loc);
        ms.setNumberOfEndpoints(endpoints);
        return ms;
    }

    @Test
    void detect_serviceBelowThresholds_flagsNanoService() {
        Project project = createProject();
        List<Microservice> services = List.of(createMs("tiny-svc", 100, 1));

        List<DetectedAntiPattern> results = detector.detect(project, services);

        assertEquals(1, results.size());
        DetectedAntiPattern pattern = results.getFirst();
        assertEquals(AntiPatternType.NANO_SERVICE, pattern.getPatternType());
        assertEquals(Severity.MEDIUM, pattern.getSeverity());
        assertTrue(pattern.getDescription().contains("tiny-svc"));
        assertTrue(pattern.getDescription().contains("100 LOC"));
    }

    @Test
    void detect_serviceAboveLocThreshold_notFlagged() {
        Project project = createProject();
        List<Microservice> services = List.of(createMs("normal-svc", 600, 1));

        List<DetectedAntiPattern> results = detector.detect(project, services);

        assertTrue(results.isEmpty());
    }

    @Test
    void detect_serviceAboveEndpointThreshold_notFlagged() {
        Project project = createProject();
        List<Microservice> services = List.of(createMs("multi-endpoint-svc", 100, 5));

        List<DetectedAntiPattern> results = detector.detect(project, services);

        assertTrue(results.isEmpty());
    }

    @Test
    void detect_serviceExactlyAtThresholds_notFlagged() {
        // LOC < 500 AND endpoints <= 2 is the condition
        // LOC=500 should NOT be flagged (needs < 500)
        Project project = createProject();
        List<Microservice> services = List.of(createMs("boundary-svc", 500, 2));

        List<DetectedAntiPattern> results = detector.detect(project, services);

        assertTrue(results.isEmpty());
    }

    @Test
    void detect_serviceJustBelowThresholds_isFlagged() {
        Project project = createProject();
        List<Microservice> services = List.of(createMs("small-svc", 499, 2));

        List<DetectedAntiPattern> results = detector.detect(project, services);

        assertEquals(1, results.size());
    }

    @Test
    void detect_multipleNanoServices_returnsMultiplePatterns() {
        Project project = createProject();
        List<Microservice> services = List.of(
                createMs("nano-1", 50, 1),
                createMs("nano-2", 80, 0),
                createMs("normal", 1000, 10)
        );

        List<DetectedAntiPattern> results = detector.detect(project, services);

        assertEquals(2, results.size());
    }

    @Test
    void detect_zeroLoc_isFlagged() {
        Project project = createProject();
        List<Microservice> services = List.of(createMs("empty-svc", 0, 0));

        List<DetectedAntiPattern> results = detector.detect(project, services);

        assertEquals(1, results.size());
    }

    @Test
    void detect_customThresholds() {
        // Use stricter thresholds
        NanoServiceDetector strictDetector = new NanoServiceDetector(new ObjectMapper(), 1000, 5);
        Project project = createProject();
        List<Microservice> services = List.of(createMs("medium-svc", 800, 3));

        List<DetectedAntiPattern> results = strictDetector.detect(project, services);

        assertEquals(1, results.size());
    }
}

