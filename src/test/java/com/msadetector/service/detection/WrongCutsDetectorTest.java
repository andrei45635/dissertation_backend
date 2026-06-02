package com.msadetector.service.detection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msadetector.entity.CodeSmell;
import com.msadetector.entity.DetectedAntiPattern;
import com.msadetector.entity.Microservice;
import com.msadetector.entity.Project;
import com.msadetector.entity.ServiceDependency;
import com.msadetector.enums.AntiPatternType;
import com.msadetector.enums.DependencyType;
import com.msadetector.enums.Severity;
import com.msadetector.enums.SourceType;
import com.msadetector.repository.CodeSmellRepository;
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
    private CodeSmellRepository codeSmellRepository;
    @Mock
    private ServiceDependencyRepository dependencyRepository;

    private WrongCutsDetector detector;

    @BeforeEach
    void setUp() {
        detector = new WrongCutsDetector(new ObjectMapper(), codeSmellRepository, dependencyRepository, 3);
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

    private CodeSmell createFeatureEnvy(Microservice ms) {
        CodeSmell smell = new CodeSmell();
        smell.setSmellType("Feature Envy");
        smell.setClassName("com.example.SomeClass");
        smell.setMicroservice(ms);
        smell.setSeverity(Severity.MEDIUM);
        return smell;
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
    void detect_featureEnvyAboveThreshold_flagsWrongCuts() {
        Project project = createProject();
        Microservice ms = createMs(1L, "svc-a");

        when(codeSmellRepository.findByMicroservice(ms))
                .thenReturn(List.of(
                        createFeatureEnvy(ms),
                        createFeatureEnvy(ms),
                        createFeatureEnvy(ms)
                ));
        when(dependencyRepository.findByProjectWithServices(project)).thenReturn(List.of());

        List<DetectedAntiPattern> results = detector.detect(project, List.of(ms));

        assertEquals(1, results.size());
        DetectedAntiPattern pattern = results.getFirst();
        assertEquals(AntiPatternType.WRONG_CUTS, pattern.getPatternType());
        assertEquals(Severity.HIGH, pattern.getSeverity());
        assertTrue(pattern.getDescription().contains("svc-a"));
        assertTrue(pattern.getDescription().contains("3 Feature Envy"));
    }

    @Test
    void detect_featureEnvyBelowThreshold_noPatternFromSmells() {
        Project project = createProject();
        Microservice ms = createMs(1L, "svc-a");

        when(codeSmellRepository.findByMicroservice(ms))
                .thenReturn(List.of(createFeatureEnvy(ms), createFeatureEnvy(ms)));
        when(dependencyRepository.findByProjectWithServices(project)).thenReturn(List.of());

        List<DetectedAntiPattern> results = detector.detect(project, List.of(ms));

        assertTrue(results.isEmpty());
    }

    @Test
    void detect_bidirectionalDependency_flagsWrongCuts() {
        Project project = createProject();
        Microservice a = createMs(1L, "svc-a");
        Microservice b = createMs(2L, "svc-b");

        when(codeSmellRepository.findByMicroservice(any())).thenReturn(List.of());
        when(dependencyRepository.findByProjectWithServices(project))
                .thenReturn(List.of(createDep(a, b), createDep(b, a)));

        List<DetectedAntiPattern> results = detector.detect(project, List.of(a, b));

        assertEquals(1, results.size());
        DetectedAntiPattern pattern = results.getFirst();
        assertEquals(AntiPatternType.WRONG_CUTS, pattern.getPatternType());
        assertTrue(pattern.getDescription().contains("Bidirectional"));
        assertTrue(pattern.getDescription().contains("svc-a"));
        assertTrue(pattern.getDescription().contains("svc-b"));
    }

    @Test
    void detect_unidirectionalDependency_noBidirPattern() {
        Project project = createProject();
        Microservice a = createMs(1L, "svc-a");
        Microservice b = createMs(2L, "svc-b");

        when(codeSmellRepository.findByMicroservice(any())).thenReturn(List.of());
        when(dependencyRepository.findByProjectWithServices(project))
                .thenReturn(List.of(createDep(a, b)));

        List<DetectedAntiPattern> results = detector.detect(project, List.of(a, b));

        assertTrue(results.isEmpty());
    }

    @Test
    void detect_bothFeatureEnvyAndBidirectional_returnsBothPatterns() {
        Project project = createProject();
        Microservice a = createMs(1L, "svc-a");
        Microservice b = createMs(2L, "svc-b");

        when(codeSmellRepository.findByMicroservice(a))
                .thenReturn(List.of(createFeatureEnvy(a), createFeatureEnvy(a), createFeatureEnvy(a)));
        when(codeSmellRepository.findByMicroservice(b)).thenReturn(List.of());
        when(dependencyRepository.findByProjectWithServices(project))
                .thenReturn(List.of(createDep(a, b), createDep(b, a)));

        List<DetectedAntiPattern> results = detector.detect(project, List.of(a, b));

        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(p -> p.getDescription().contains("Feature Envy")));
        assertTrue(results.stream().anyMatch(p -> p.getDescription().contains("Bidirectional")));
    }

    @Test
    void detect_multipleBidirectionalPairs_eachReportedOnce() {
        Project project = createProject();
        Microservice a = createMs(1L, "a");
        Microservice b = createMs(2L, "b");
        Microservice c = createMs(3L, "c");
        Microservice d = createMs(4L, "d");

        when(codeSmellRepository.findByMicroservice(any())).thenReturn(List.of());
        when(dependencyRepository.findByProjectWithServices(project))
                .thenReturn(List.of(
                        createDep(a, b), createDep(b, a),
                        createDep(c, d), createDep(d, c)
                ));

        List<DetectedAntiPattern> results = detector.detect(project, List.of(a, b, c, d));

        assertEquals(2, results.size());
    }
}

