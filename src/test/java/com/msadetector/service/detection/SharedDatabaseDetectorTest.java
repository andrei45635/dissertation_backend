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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SharedDatabaseDetectorTest {

    private SharedDatabaseDetector detector;
    private ObjectMapper objectMapper;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        detector = new SharedDatabaseDetector(objectMapper);
    }

    private Project createProject() {
        Project project = new Project();
        project.setName("test-project");
        project.setSourceType(SourceType.UPLOAD);
        project.setLocalPath(tempDir.toString());
        return project;
    }

    private Microservice createMicroservice(String name, String datasourceUrl) {
        Microservice ms = new Microservice();
        ms.setName(name);
        ms.setRelativePath(name);
        ms.setDatasourceUrl(datasourceUrl);
        return ms;
    }

    @Test
    void detect_noSharedDatabases_returnsEmpty() {
        Project project = createProject();
        List<Microservice> services = List.of(
                createMicroservice("service-a", "jdbc:postgresql://localhost/db_a"),
                createMicroservice("service-b", "jdbc:postgresql://localhost/db_b")
        );

        List<DetectedAntiPattern> results = detector.detect(project, services);

        assertTrue(results.isEmpty());
    }

    @Test
    void detect_twoServicesShareDatabase_returnsOnePattern() {
        Project project = createProject();
        String sharedUrl = "jdbc:postgresql://localhost/shared_db";
        List<Microservice> services = List.of(
                createMicroservice("service-a", sharedUrl),
                createMicroservice("service-b", sharedUrl)
        );

        List<DetectedAntiPattern> results = detector.detect(project, services);

        assertEquals(1, results.size());
        DetectedAntiPattern pattern = results.getFirst();
        assertEquals(AntiPatternType.SHARED_DATABASE, pattern.getPatternType());
        assertEquals(Severity.HIGH, pattern.getSeverity());
        assertEquals(sharedUrl, pattern.getSharedDatabaseUrl());
        assertTrue(pattern.getDescription().contains("shared_db"));
        assertTrue(pattern.getAffectedServicesJson().contains("service-a"));
        assertTrue(pattern.getAffectedServicesJson().contains("service-b"));
    }

    @Test
    void detect_threeServicesShareDatabase_returnsOnePatternWithAllThree() {
        Project project = createProject();
        String sharedUrl = "jdbc:mysql://host/common";
        List<Microservice> services = List.of(
                createMicroservice("svc-1", sharedUrl),
                createMicroservice("svc-2", sharedUrl),
                createMicroservice("svc-3", sharedUrl)
        );

        List<DetectedAntiPattern> results = detector.detect(project, services);

        assertEquals(1, results.size());
        assertTrue(results.getFirst().getAffectedServicesJson().contains("svc-1"));
        assertTrue(results.getFirst().getAffectedServicesJson().contains("svc-2"));
        assertTrue(results.getFirst().getAffectedServicesJson().contains("svc-3"));
    }

    @Test
    void detect_twoGroupsShareDifferentDatabases_returnsTwoPatterns() {
        Project project = createProject();
        List<Microservice> services = List.of(
                createMicroservice("a1", "jdbc:pg://host/db1"),
                createMicroservice("a2", "jdbc:pg://host/db1"),
                createMicroservice("b1", "jdbc:pg://host/db2"),
                createMicroservice("b2", "jdbc:pg://host/db2")
        );

        List<DetectedAntiPattern> results = detector.detect(project, services);

        assertEquals(2, results.size());
    }

    @Test
    void detect_nullAndBlankDatasourceUrls_areIgnored() {
        Project project = createProject();
        List<Microservice> services = List.of(
                createMicroservice("svc-1", null),
                createMicroservice("svc-2", ""),
                createMicroservice("svc-3", "   ")
        );

        List<DetectedAntiPattern> results = detector.detect(project, services);

        assertTrue(results.isEmpty());
    }

    @Test
    void detect_singleService_noPattern() {
        Project project = createProject();
        List<Microservice> services = List.of(
                createMicroservice("only-svc", "jdbc:pg://host/db")
        );

        List<DetectedAntiPattern> results = detector.detect(project, services);

        assertTrue(results.isEmpty());
    }

    @Test
    void detect_withConfigFile_readsSnippet() throws Exception {
        Project project = createProject();
        String sharedUrl = "jdbc:postgresql://localhost/shared";

        Path svcADir = tempDir.resolve("svc-a/src/main/resources");
        Files.createDirectories(svcADir);
        Files.writeString(svcADir.resolve("application.yml"),
                "spring:\n  datasource:\n    url: " + sharedUrl + "\n");

        Path svcBDir = tempDir.resolve("svc-b/src/main/resources");
        Files.createDirectories(svcBDir);
        Files.writeString(svcBDir.resolve("application.yml"),
                "spring:\n  datasource:\n    url: " + sharedUrl + "\n");

        List<Microservice> services = List.of(
                createMicroservice("svc-a", sharedUrl),
                createMicroservice("svc-b", sharedUrl)
        );

        List<DetectedAntiPattern> results = detector.detect(project, services);

        assertEquals(1, results.size());
        assertNotNull(results.getFirst().getCodeSnippetsJson());
    }
}

