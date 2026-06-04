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
class WrongCutsDetectorTest {

    @Mock
    private ServiceDependencyRepository dependencyRepository;

    private WrongCutsDetector detector;

    @BeforeEach
    void setUp() {
        detector = new WrongCutsDetector(new ObjectMapper(), dependencyRepository);
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
    void detect_bidirectionalDependency_flagsWrongCuts() {
        Project project = createProject();
        Microservice a = createMs(1L, "svc-a");
        Microservice b = createMs(2L, "svc-b");

        when(dependencyRepository.findByProjectWithServices(project))
                .thenReturn(List.of(createDep(a, b), createDep(b, a)));

        List<DetectedAntiPattern> results = detector.detect(project, List.of(a, b));

        assertEquals(1, results.size());
        DetectedAntiPattern pattern = results.getFirst();
        assertEquals(AntiPatternType.WRONG_CUTS, pattern.getPatternType());
        assertEquals(Severity.HIGH, pattern.getSeverity());
        assertTrue(pattern.getDescription().contains("Bidirectional"));
        assertTrue(pattern.getDescription().contains("svc-a"));
        assertTrue(pattern.getDescription().contains("svc-b"));
    }

    @Test
    void detect_unidirectionalDependency_noPattern() {
        Project project = createProject();
        Microservice a = createMs(1L, "svc-a");
        Microservice b = createMs(2L, "svc-b");

        when(dependencyRepository.findByProjectWithServices(project))
                .thenReturn(List.of(createDep(a, b)));

        List<DetectedAntiPattern> results = detector.detect(project, List.of(a, b));

        assertTrue(results.isEmpty());
    }

    @Test
    void detect_multipleBidirectionalPairs_eachReportedOnce() {
        Project project = createProject();
        Microservice a = createMs(1L, "a");
        Microservice b = createMs(2L, "b");
        Microservice c = createMs(3L, "c");
        Microservice d = createMs(4L, "d");

        when(dependencyRepository.findByProjectWithServices(project))
                .thenReturn(List.of(
                        createDep(a, b), createDep(b, a),
                        createDep(c, d), createDep(d, c)
                ));

        List<DetectedAntiPattern> results = detector.detect(project, List.of(a, b, c, d));

        assertEquals(2, results.size());
    }

    @Test
    void detect_noDependencies_returnsEmpty() {
        Project project = createProject();
        Microservice a = createMs(1L, "svc-a");

        when(dependencyRepository.findByProjectWithServices(project)).thenReturn(List.of());

        List<DetectedAntiPattern> results = detector.detect(project, List.of(a));

        assertTrue(results.isEmpty());
    }
}
