package com.msadetector.service.detection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msadetector.entity.DetectedAntiPattern;
import com.msadetector.entity.Endpoint;
import com.msadetector.entity.Microservice;
import com.msadetector.entity.Project;
import com.msadetector.enums.AntiPatternType;
import com.msadetector.enums.HttpMethod;
import com.msadetector.enums.Severity;
import com.msadetector.enums.SourceType;
import com.msadetector.repository.EndpointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiVersioningDetectorTest {

    @Mock
    private EndpointRepository endpointRepository;

    private ApiVersioningDetector detector;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        detector = new ApiVersioningDetector(new ObjectMapper(), endpointRepository);
    }

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

    private Endpoint createEndpoint(String path, boolean hasVersioning) {
        Endpoint ep = new Endpoint();
        ep.setPath(path);
        ep.setHttpMethod(HttpMethod.GET);
        ep.setHasVersioning(hasVersioning);
        ep.setControllerClass("com.example.MyController");
        return ep;
    }

    private Endpoint createEndpoint(String path, String apiVersion) {
        Endpoint ep = createEndpoint(path, false);
        ep.setApiVersion(apiVersion);
        return ep;
    }

    @Test
    void detect_noVersioning_flagsPattern() {
        Project project = createProject();
        Microservice ms = createMs("svc");

        when(endpointRepository.findByMicroservice(ms))
                .thenReturn(List.of(
                        createEndpoint("/api/users", false),
                        createEndpoint("/api/orders", false)
                ));

        List<DetectedAntiPattern> results = detector.detect(project, List.of(ms));

        assertEquals(1, results.size());
        DetectedAntiPattern pattern = results.getFirst();
        assertEquals(AntiPatternType.API_VERSIONING_ABSENCE, pattern.getPatternType());
        assertEquals(Severity.MEDIUM, pattern.getSeverity());
        assertTrue(pattern.getDescription().contains("svc"));
        assertTrue(pattern.getDescription().contains("2 endpoint"));
    }

    @Test
    void detect_allVersioned_returnsEmpty() {
        Project project = createProject();
        Microservice ms = createMs("svc");

        when(endpointRepository.findByMicroservice(ms))
                .thenReturn(List.of(
                        createEndpoint("/v1/api/users", true),
                        createEndpoint("/v2/api/users", true)
                ));

        List<DetectedAntiPattern> results = detector.detect(project, List.of(ms));

        assertTrue(results.isEmpty());
    }

    @Test
    void detect_mixedVersioning_notFlagged() {

        Project project = createProject();
        Microservice ms = createMs("svc");

        when(endpointRepository.findByMicroservice(ms))
                .thenReturn(List.of(
                        createEndpoint("/v1/api/users", true),
                        createEndpoint("/api/legacy", false)
                ));

        List<DetectedAntiPattern> results = detector.detect(project, List.of(ms));

        assertTrue(results.isEmpty());
    }

    @Test
    void detect_headerVersioningMetadata_returnsEmpty() {
        Project project = createProject();
        Microservice ms = createMs("svc");

        when(endpointRepository.findByMicroservice(ms))
                .thenReturn(List.of(createEndpoint("/api/users", "header")));

        List<DetectedAntiPattern> results = detector.detect(project, List.of(ms));

        assertTrue(results.isEmpty());
    }

    @Test
    void detect_queryVersioningMetadata_returnsEmpty() {
        Project project = createProject();
        Microservice ms = createMs("svc");

        when(endpointRepository.findByMicroservice(ms))
                .thenReturn(List.of(createEndpoint("/api/users", "query")));

        List<DetectedAntiPattern> results = detector.detect(project, List.of(ms));

        assertTrue(results.isEmpty());
    }

    @Test
    void detect_noEndpoints_returnsEmpty() {
        Project project = createProject();
        Microservice ms = createMs("svc");

        when(endpointRepository.findByMicroservice(ms)).thenReturn(List.of());

        List<DetectedAntiPattern> results = detector.detect(project, List.of(ms));

        assertTrue(results.isEmpty());
    }

    @Test
    void detect_multipleServices_flagsOnlyUnversioned() {
        Project project = createProject();
        Microservice versioned = createMs("versioned-svc");
        Microservice unversioned = createMs("unversioned-svc");

        when(endpointRepository.findByMicroservice(versioned))
                .thenReturn(List.of(createEndpoint("/v1/api/data", true)));
        when(endpointRepository.findByMicroservice(unversioned))
                .thenReturn(List.of(createEndpoint("/api/data", false)));

        List<DetectedAntiPattern> results = detector.detect(project, List.of(versioned, unversioned));

        assertEquals(1, results.size());
        assertTrue(results.getFirst().getDescription().contains("unversioned-svc"));
    }
}
