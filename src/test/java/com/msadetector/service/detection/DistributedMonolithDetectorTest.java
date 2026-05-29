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
class DistributedMonolithDetectorTest {

    @Mock
    private ServiceDependencyRepository dependencyRepository;

    private DistributedMonolithDetector detector;

    @BeforeEach
    void setUp() {
        detector = new DistributedMonolithDetector(new ObjectMapper(), dependencyRepository);
    }

    private Project createProject() {
        Project project = new Project();
        project.setName("test-project");
        project.setSourceType(SourceType.UPLOAD);
        project.setLocalPath("/tmp/test");
        return project;
    }

    private Microservice createMs(Long id, String name, String datasourceUrl) {
        Microservice ms = new Microservice();
        ms.setId(id);
        ms.setName(name);
        ms.setRelativePath(name);
        ms.setDatasourceUrl(datasourceUrl);
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
    void detect_fewerThan3Services_returnsEmpty() {
        Project project = createProject();
        Microservice a = createMs(1L, "a", null);
        Microservice b = createMs(2L, "b", null);

        List<DetectedAntiPattern> results = detector.detect(project, List.of(a, b));

        assertTrue(results.isEmpty());
        verifyNoInteractions(dependencyRepository);
    }

    @Test
    void detect_highCouplingCoefficient_flagsMonolith() {
        Project project = createProject();
        Microservice a = createMs(1L, "a", null);
        Microservice b = createMs(2L, "b", null);
        Microservice c = createMs(3L, "c", null);

        // 3 services, max edges = 3*2 = 6
        // All 6 edges present → coupling = 1.0 > 0.5
        when(dependencyRepository.findByProjectWithServices(project))
                .thenReturn(List.of(
                        createDep(a, b), createDep(a, c),
                        createDep(b, a), createDep(b, c),
                        createDep(c, a), createDep(c, b)
                ));

        List<DetectedAntiPattern> results = detector.detect(project, List.of(a, b, c));

        assertEquals(1, results.size());
        DetectedAntiPattern pattern = results.getFirst();
        assertEquals(AntiPatternType.DISTRIBUTED_MONOLITH, pattern.getPatternType());
        assertEquals(Severity.CRITICAL, pattern.getSeverity());
        assertTrue(pattern.getDescription().contains("Coupling coefficient"));
    }

    @Test
    void detect_lowCoupling_returnsEmpty() {
        Project project = createProject();
        Microservice a = createMs(1L, "a", null);
        Microservice b = createMs(2L, "b", null);
        Microservice c = createMs(3L, "c", null);
        Microservice d = createMs(4L, "d", null);

        // 4 services, max edges = 12, only 1 edge → 0.083 coupling
        when(dependencyRepository.findByProjectWithServices(project))
                .thenReturn(List.of(createDep(a, b)));

        List<DetectedAntiPattern> results = detector.detect(project, List.of(a, b, c, d));

        assertTrue(results.isEmpty());
    }

    @Test
    void detect_highConnectedRatioWithSharedDb_flagsMonolith() {
        Project project = createProject();
        String sharedDb = "jdbc:pg://host/shared";
        Microservice a = createMs(1L, "a", sharedDb);
        Microservice b = createMs(2L, "b", sharedDb);
        Microservice c = createMs(3L, "c", null);

        // connectedRatio > 0.8 and sharedDbCount > 0
        // 3 services, 3 edges, all 3 connected → 100%
        when(dependencyRepository.findByProjectWithServices(project))
                .thenReturn(List.of(createDep(a, b), createDep(b, c), createDep(c, a)));

        List<DetectedAntiPattern> results = detector.detect(project, List.of(a, b, c));

        assertEquals(1, results.size());
    }

    @Test
    void detect_noDependencies_returnsEmpty() {
        Project project = createProject();
        Microservice a = createMs(1L, "a", null);
        Microservice b = createMs(2L, "b", null);
        Microservice c = createMs(3L, "c", null);

        when(dependencyRepository.findByProjectWithServices(project))
                .thenReturn(List.of());

        List<DetectedAntiPattern> results = detector.detect(project, List.of(a, b, c));

        assertTrue(results.isEmpty());
    }
}

