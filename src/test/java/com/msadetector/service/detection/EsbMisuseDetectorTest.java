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
class EsbMisuseDetectorTest {

    @Mock
    private ServiceDependencyRepository dependencyRepository;

    private EsbMisuseDetector detector;

    @BeforeEach
    void setUp() {
        detector = new EsbMisuseDetector(new ObjectMapper(), dependencyRepository, 0.4);
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
    void detect_fewerThan3Services_returnsEmpty() {
        Project project = createProject();
        Microservice a = createMs(1L, "a");
        Microservice b = createMs(2L, "b");

        List<DetectedAntiPattern> results = detector.detect(project, List.of(a, b));

        assertTrue(results.isEmpty());
    }

    @Test
    void detect_noDependencies_returnsEmpty() {
        Project project = createProject();
        Microservice a = createMs(1L, "a");
        Microservice b = createMs(2L, "b");
        Microservice c = createMs(3L, "c");

        when(dependencyRepository.findByProjectWithServices(project)).thenReturn(List.of());

        List<DetectedAntiPattern> results = detector.detect(project, List.of(a, b, c));

        assertTrue(results.isEmpty());
    }

    @Test
    void detect_centralHub_flagsEsbMisuse() {
        Project project = createProject();
        Microservice hub = createMs(1L, "orchestrator");
        Microservice a = createMs(2L, "svc-a");
        Microservice b = createMs(3L, "svc-b");
        Microservice c = createMs(4L, "svc-c");
        Microservice d = createMs(5L, "svc-d");

        List<ServiceDependency> deps = List.of(
                createDep(a, hub), createDep(b, hub), createDep(c, hub), createDep(d, hub),
                createDep(hub, a), createDep(hub, b), createDep(hub, c), createDep(hub, d)
        );
        when(dependencyRepository.findByProjectWithServices(project)).thenReturn(deps);

        List<DetectedAntiPattern> results = detector.detect(project, List.of(hub, a, b, c, d));

        assertFalse(results.isEmpty());
        DetectedAntiPattern pattern = results.stream()
                .filter(p -> p.getDescription().contains("orchestrator"))
                .findFirst().orElse(null);
        assertNotNull(pattern);
        assertEquals(AntiPatternType.ESB_MISUSE, pattern.getPatternType());
        assertEquals(Severity.HIGH, pattern.getSeverity());
    }

    @Test
    void detect_gatewayService_isSkipped() {
        Project project = createProject();
        Microservice gateway = createMs(1L, "api-gateway");
        Microservice a = createMs(2L, "svc-a");
        Microservice b = createMs(3L, "svc-b");
        Microservice c = createMs(4L, "svc-c");

        List<ServiceDependency> deps = List.of(
                createDep(a, gateway), createDep(b, gateway), createDep(c, gateway),
                createDep(gateway, a), createDep(gateway, b), createDep(gateway, c)
        );
        when(dependencyRepository.findByProjectWithServices(project)).thenReturn(deps);

        List<DetectedAntiPattern> results = detector.detect(project, List.of(gateway, a, b, c));

        assertTrue(results.stream().noneMatch(p -> p.getDescription().contains("api-gateway")));
    }

    @Test
    void detect_noHub_returnsEmpty() {
        Project project = createProject();
        Microservice a = createMs(1L, "a");
        Microservice b = createMs(2L, "b");
        Microservice c = createMs(3L, "c");
        Microservice d = createMs(4L, "d");
        Microservice e = createMs(5L, "e");
        Microservice f = createMs(6L, "f");

        when(dependencyRepository.findByProjectWithServices(project))
                .thenReturn(List.of(
                        createDep(a, b), createDep(c, d),
                        createDep(e, f), createDep(b, a),
                        createDep(d, c), createDep(f, e)
                ));

        List<DetectedAntiPattern> results = detector.detect(project, List.of(a, b, c, d, e, f));

        assertTrue(results.isEmpty());
    }

    @Test
    void detect_gatewayKeywordsAreAllSkipped() {
        Project project = createProject();
        for (String gwName : List.of("gateway", "zuul", "edge-service", "bff", "proxy")) {
            Microservice gw = createMs(1L, gwName);
            Microservice a = createMs(2L, "svc-a");
            Microservice b = createMs(3L, "svc-b");
            Microservice c = createMs(4L, "svc-c");

            List<ServiceDependency> deps = List.of(
                    createDep(a, gw), createDep(b, gw), createDep(c, gw),
                    createDep(gw, a), createDep(gw, b), createDep(gw, c)
            );
            when(dependencyRepository.findByProjectWithServices(project)).thenReturn(deps);

            List<DetectedAntiPattern> results = detector.detect(project, List.of(gw, a, b, c));

            assertTrue(results.stream().noneMatch(p -> p.getDescription().contains(gwName)),
                    "Gateway name '" + gwName + "' should be skipped");
        }
    }
}

