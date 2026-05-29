package com.msadetector.service.detection;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CyclicDependencyDetectorTest {

    @Mock
    private ServiceDependencyRepository dependencyRepository;

    private CyclicDependencyDetector detector;

    @BeforeEach
    void setUp() {
        detector = new CyclicDependencyDetector(new ObjectMapper(), dependencyRepository);
    }

    private Project createProject() {
        Project project = new Project();
        project.setName("test-project");
        project.setSourceType(SourceType.UPLOAD);
        project.setLocalPath("/tmp/test");
        return project;
    }

    private Microservice createMs(Long id, String name) {
        Microservice ms = new Microservice();
        ms.setId(id);
        ms.setName(name);
        ms.setRelativePath(name);
        return ms;
    }

    private ServiceDependency createDep(Microservice source, Microservice target) {
        ServiceDependency dep = new ServiceDependency();
        dep.setSourceService(source);
        dep.setTargetService(target);
        dep.setDependencyType(DependencyType.REST_TEMPLATE);
        dep.setCallCount(1);
        return dep;
    }

    @Test
    void detect_noCycle_returnsEmpty() {
        Project project = createProject();
        Microservice a = createMs(1L, "service-a");
        Microservice b = createMs(2L, "service-b");

        // A -> B (no cycle)
        when(dependencyRepository.findByProjectWithServices(project))
                .thenReturn(List.of(createDep(a, b)));

        List<DetectedAntiPattern> results = detector.detect(project, List.of(a, b));

        assertTrue(results.isEmpty());
    }

    @Test
    void detect_simpleCycle_returnsOnePattern() {
        Project project = createProject();
        Microservice a = createMs(1L, "service-a");
        Microservice b = createMs(2L, "service-b");

        // A -> B -> A (cycle)
        when(dependencyRepository.findByProjectWithServices(project))
                .thenReturn(List.of(createDep(a, b), createDep(b, a)));

        List<DetectedAntiPattern> results = detector.detect(project, List.of(a, b));

        assertEquals(1, results.size());
        DetectedAntiPattern pattern = results.getFirst();
        assertEquals(AntiPatternType.CYCLIC_DEPENDENCY, pattern.getPatternType());
        assertEquals(Severity.CRITICAL, pattern.getSeverity());
        assertEquals(2, pattern.getCycleLength());
        assertTrue(pattern.getDescription().contains("Cyclic dependency"));
    }

    @Test
    void detect_threeNodeCycle_returnsOnePatternWithLength3() {
        Project project = createProject();
        Microservice a = createMs(1L, "svc-a");
        Microservice b = createMs(2L, "svc-b");
        Microservice c = createMs(3L, "svc-c");

        // A -> B -> C -> A
        when(dependencyRepository.findByProjectWithServices(project))
                .thenReturn(List.of(createDep(a, b), createDep(b, c), createDep(c, a)));

        List<DetectedAntiPattern> results = detector.detect(project, List.of(a, b, c));

        assertEquals(1, results.size());
        assertEquals(3, results.getFirst().getCycleLength());
    }

    @Test
    void detect_noDependencies_returnsEmpty() {
        Project project = createProject();
        Microservice a = createMs(1L, "svc-a");

        when(dependencyRepository.findByProjectWithServices(project))
                .thenReturn(List.of());

        List<DetectedAntiPattern> results = detector.detect(project, List.of(a));

        assertTrue(results.isEmpty());
    }

    @Test
    void detect_twoCycles_returnsTwoPatterns() {
        Project project = createProject();
        Microservice a = createMs(1L, "a");
        Microservice b = createMs(2L, "b");
        Microservice c = createMs(3L, "c");
        Microservice d = createMs(4L, "d");

        // Cycle 1: A <-> B, Cycle 2: C <-> D
        when(dependencyRepository.findByProjectWithServices(project))
                .thenReturn(List.of(
                        createDep(a, b), createDep(b, a),
                        createDep(c, d), createDep(d, c)
                ));

        List<DetectedAntiPattern> results = detector.detect(project, List.of(a, b, c, d));

        assertEquals(2, results.size());
    }

    @Test
    void detect_cycleWithEvidence_includesSnippets() {
        Project project = createProject();
        Microservice a = createMs(1L, "svc-a");
        Microservice b = createMs(2L, "svc-b");

        ServiceDependency depAB = createDep(a, b);
        depAB.setEvidenceFile("svc-a/src/main/java/Client.java");
        depAB.setEvidenceCode("restTemplate.getForObject(\"http://svc-b/api\")");

        ServiceDependency depBA = createDep(b, a);
        depBA.setEvidenceFile("svc-b/src/main/java/Client.java");
        depBA.setEvidenceCode("restTemplate.getForObject(\"http://svc-a/api\")");

        when(dependencyRepository.findByProjectWithServices(project))
                .thenReturn(List.of(depAB, depBA));

        List<DetectedAntiPattern> results = detector.detect(project, List.of(a, b));

        assertEquals(1, results.size());
        // Snippets are built from evidence code (files don't exist on disk)
        assertNotNull(results.getFirst().getCodeSnippetsJson());
    }
}

