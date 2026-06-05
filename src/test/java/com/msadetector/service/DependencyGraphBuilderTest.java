package com.msadetector.service;

import com.msadetector.entity.AnalysisJob;
import com.msadetector.entity.Microservice;
import com.msadetector.entity.Project;
import com.msadetector.entity.ServiceDependency;
import com.msadetector.enums.DependencyType;
import com.msadetector.enums.SourceType;
import com.msadetector.repository.EndpointRepository;
import com.msadetector.repository.MicroserviceRepository;
import com.msadetector.repository.ServiceDependencyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DependencyGraphBuilderTest {

    @Mock
    private MicroserviceRepository microserviceRepository;

    @Mock
    private ServiceDependencyRepository dependencyRepository;

    @Mock
    private EndpointRepository endpointRepository;

    private DependencyGraphBuilder builder;

    @TempDir
    Path root;

    @BeforeEach
    void setUp() {
        builder = new DependencyGraphBuilder(microserviceRepository, dependencyRepository, endpointRepository);
    }

    @Test
    void buildDependencyGraph_feignUrlToLocalhost_resolvesTargetByPort() throws Exception {
        AnalysisJob job = new AnalysisJob();
        Project project = createProject();
        Microservice source = createService(1L, "order-service");
        Microservice target = createService(2L, "payment-service");

        createServiceLayout(source.getRelativePath(), null, """
                package com.example;

                import org.springframework.cloud.openfeign.FeignClient;
                import org.springframework.web.bind.annotation.GetMapping;

                @FeignClient(url = "http://localhost:8081")
                interface PaymentClient {
                    @GetMapping("/payments")
                    String listPayments();
                }
                """);
        createServiceLayout(target.getRelativePath(), "server.port=8081\n", """
                package com.example;
                class PaymentApplication {}
                """);

        when(microserviceRepository.findByAnalysisJob(job)).thenReturn(List.of(source, target));

        builder.buildDependencyGraph(project, job);

        ArgumentCaptor<ServiceDependency> captor = ArgumentCaptor.forClass(ServiceDependency.class);
        verify(dependencyRepository).save(captor.capture());
        ServiceDependency dependency = captor.getValue();
        assertEquals(source, dependency.getSourceService());
        assertEquals(target, dependency.getTargetService());
        assertEquals(DependencyType.FEIGN_CLIENT, dependency.getDependencyType());
        assertEquals("http://localhost:8081", dependency.getTargetUrl());
    }

    @Test
    void buildDependencyGraph_feignUrlPlaceholder_resolvesDefaultUrlByPort() throws Exception {
        AnalysisJob job = new AnalysisJob();
        Project project = createProject();
        Microservice source = createService(1L, "order-service");
        Microservice target = createService(2L, "payment-service");

        createServiceLayout(source.getRelativePath(), null, """
                package com.example;

                import org.springframework.cloud.openfeign.FeignClient;
                import org.springframework.web.bind.annotation.GetMapping;

                @FeignClient(name = "payment", url = "${payment.url:http://localhost:8081}")
                interface PaymentClient {
                    @GetMapping("/payments")
                    String listPayments();
                }
                """);
        createServiceLayout(target.getRelativePath(), "server.port=8081\n", """
                package com.example;
                class PaymentApplication {}
                """);

        when(microserviceRepository.findByAnalysisJob(job)).thenReturn(List.of(source, target));

        builder.buildDependencyGraph(project, job);

        ArgumentCaptor<ServiceDependency> captor = ArgumentCaptor.forClass(ServiceDependency.class);
        verify(dependencyRepository).save(captor.capture());
        ServiceDependency dependency = captor.getValue();
        assertEquals(target, dependency.getTargetService());
        assertEquals("http://localhost:8081", dependency.getTargetUrl());
    }

    private Project createProject() {
        Project project = new Project();
        project.setName("project");
        project.setSourceType(SourceType.UPLOAD);
        project.setLocalPath(root.toString());
        return project;
    }

    private Microservice createService(Long id, String name) {
        Microservice microservice = new Microservice();
        microservice.setId(id);
        microservice.setName(name);
        microservice.setRelativePath(name);
        return microservice;
    }

    private void createServiceLayout(String serviceName, String properties, String javaSource) throws Exception {
        Path serviceRoot = root.resolve(serviceName);
        Path resources = serviceRoot.resolve("src/main/resources");
        Path javaDir = serviceRoot.resolve("src/main/java/com/example");
        Files.createDirectories(resources);
        Files.createDirectories(javaDir);
        if (properties != null) {
            Files.writeString(resources.resolve("application.properties"), properties);
        }
        Files.writeString(javaDir.resolve("Client.java"), javaSource);
    }
}
