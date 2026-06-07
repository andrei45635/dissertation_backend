package com.msadetector.service.detection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msadetector.entity.AnalysisJob;
import com.msadetector.entity.DetectedAntiPattern;
import com.msadetector.entity.Microservice;
import com.msadetector.entity.Project;
import com.msadetector.entity.ServiceDependency;
import com.msadetector.enums.AntiPatternType;
import com.msadetector.enums.DependencyType;
import com.msadetector.enums.Severity;
import com.msadetector.enums.SourceType;
import com.msadetector.repository.ServiceDependencyRepository;
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
class ChattyServiceDetectorTest {

    @Mock
    private ServiceDependencyRepository dependencyRepository;

    private ChattyServiceDetector detector;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        detector = new ChattyServiceDetector(new ObjectMapper(), dependencyRepository, 10);
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

    private ServiceDependency createChattyDep(Microservice source, Microservice target, int callCount) {
        ServiceDependency dep = new ServiceDependency();
        dep.setSourceService(source);
        dep.setTargetService(target);
        dep.setDependencyType(DependencyType.REST_TEMPLATE);
        dep.setCallCount(callCount);
        return dep;
    }

    @Test
    void detect_chattyDependency_returnsPattern() {
        Project project = createProject();
        Microservice a = createMs("service-a");
        Microservice b = createMs("service-b");

        ServiceDependency chattyDep = createChattyDep(a, b, 15);
        when(dependencyRepository.findChattyDependencies(project, 10))
                .thenReturn(List.of(chattyDep));

        List<DetectedAntiPattern> results = detector.detect(project, List.of(a, b));

        assertEquals(1, results.size());
        DetectedAntiPattern pattern = results.getFirst();
        assertEquals(AntiPatternType.CHATTY_SERVICE, pattern.getPatternType());
        assertEquals(Severity.HIGH, pattern.getSeverity());
        assertEquals(15, pattern.getCallCount());
        assertTrue(pattern.getDescription().contains("service-a"));
        assertTrue(pattern.getDescription().contains("service-b"));
        assertTrue(pattern.getDescription().contains("15 calls"));
    }

    @Test
    void detect_noChattyDependencies_returnsEmpty() {
        Project project = createProject();
        Microservice a = createMs("svc-a");

        when(dependencyRepository.findChattyDependencies(project, 10))
                .thenReturn(List.of());

        List<DetectedAntiPattern> results = detector.detect(project, List.of(a));

        assertTrue(results.isEmpty());
    }

    @Test
    void detect_multipleChattyPairs_returnsMultiplePatterns() {
        Project project = createProject();
        Microservice a = createMs("svc-a");
        Microservice b = createMs("svc-b");
        Microservice c = createMs("svc-c");

        when(dependencyRepository.findChattyDependencies(project, 10))
                .thenReturn(List.of(
                        createChattyDep(a, b, 20),
                        createChattyDep(b, c, 12)
                ));

        List<DetectedAntiPattern> results = detector.detect(project, List.of(a, b, c));

        assertEquals(2, results.size());
    }

    @Test
    void detect_chattyDepWithEvidence_includesSnippet() {
        Project project = createProject();
        Microservice a = createMs("svc-a");
        Microservice b = createMs("svc-b");

        ServiceDependency dep = createChattyDep(a, b, 10);
        dep.setEvidenceCode("restTemplate.getForObject(url, String.class)");
        dep.setEvidenceFile("svc-a/SomeClient.java");
        dep.setEvidenceLine(0);

        when(dependencyRepository.findChattyDependencies(project, 10))
                .thenReturn(List.of(dep));

        List<DetectedAntiPattern> results = detector.detect(project, List.of(a, b));

        assertEquals(1, results.size());
        assertNotNull(results.getFirst().getCodeSnippetsJson());
    }

    @Test
    void detect_jobThresholdOverridesConfiguredDefault() {
        Project project = createProject();
        Microservice a = createMs("service-a");
        Microservice b = createMs("service-b");
        AnalysisJob job = AnalysisJob.builder()
                .chattyServiceMinCalls(4)
                .build();

        ServiceDependency chattyDep = createChattyDep(a, b, 5);
        when(dependencyRepository.findChattyDependenciesByAnalysisJob(job, 4))
                .thenReturn(List.of(chattyDep));

        List<DetectedAntiPattern> results = detector.detect(project, List.of(a, b), job);

        assertEquals(1, results.size());
        verify(dependencyRepository).findChattyDependenciesByAnalysisJob(job, 4);
    }
}

