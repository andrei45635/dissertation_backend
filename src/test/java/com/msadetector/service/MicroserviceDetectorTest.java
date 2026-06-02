package com.msadetector.service;

import com.msadetector.service.MicroserviceDetector.DetectedService;
import com.msadetector.service.MicroserviceDetector.DetectionConfidence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MicroserviceDetectorTest {

    private MicroserviceDetector detector;

    @BeforeEach
    void setUp() {
        detector = new MicroserviceDetector();
    }

    @Test
    void detectsSpringBootApplication(@TempDir Path root) throws IOException {
        Path module = createModule(root, "user-service");
        writeJavaFile(module, "com/example", "UserServiceApplication.java",
                """
                package com.example;
                import org.springframework.boot.autoconfigure.SpringBootApplication;
                @SpringBootApplication
                public class UserServiceApplication {
                    public static void main(String[] args) {}
                }
                """);

        List<DetectedService> result = detector.detectServicesWithConfidence(root);

        assertEquals(1, result.size());
        assertEquals("user-service", result.get(0).path().getFileName().toString());
        assertEquals(DetectionConfidence.HIGH, result.get(0).confidence());
        assertEquals("framework-entry-point", result.get(0).signal());
    }

    @Test
    void detectsQuarkusMain(@TempDir Path root) throws IOException {
        Path module = createModule(root, "order-service");
        writeJavaFile(module, "com/example", "App.java",
                """
                package com.example;
                @QuarkusMain
                public class App {
                    public static void main(String[] args) {}
                }
                """);

        List<DetectedService> result = detector.detectServicesWithConfidence(root);

        assertEquals(1, result.size());
        assertEquals(DetectionConfidence.HIGH, result.get(0).confidence());
    }

    @Test
    void ignoresAnnotationInComments(@TempDir Path root) throws IOException {
        Path module = createModule(root, "lib-common");
        writeJavaFile(module, "com/example", "Config.java",
                """
                package com.example;
                // @SpringBootApplication — not actually annotated
                /* @SpringBootApplication */
                public class Config {}
                """);

        List<DetectedService> result = detector.detectServicesWithConfidence(root);

        assertEquals(1, result.size());
        assertEquals(DetectionConfidence.LOW, result.get(0).confidence());
        assertEquals("single-module-fallback", result.get(0).signal());
    }

    @Test
    void detectsDockerfile(@TempDir Path root) throws IOException {
        Path module = createModule(root, "api-gateway");
        Files.writeString(module.resolve("Dockerfile"), "FROM openjdk:17\nCOPY target/*.jar app.jar\n");
        writeJavaFile(module, "com/example", "Config.java",
                """
                package com.example;
                public class Config { }
                """);

        Path module2 = createModule(root, "web-service");
        writeJavaFile(module2, "com/example", "WebApp.java",
                """
                package com.example;
                @SpringBootApplication
                public class WebApp {}
                """);

        List<DetectedService> result = detector.detectServicesWithConfidence(root);

        DetectedService gateway = result.stream()
                .filter(s -> s.path().getFileName().toString().equals("api-gateway"))
                .findFirst().orElseThrow();
        assertEquals(DetectionConfidence.MEDIUM, gateway.confidence());
        assertEquals("dockerfile", gateway.signal());
    }

    @Test
    void detectsDockerfileVariant(@TempDir Path root) throws IOException {
        Path module = createModule(root, "svc");
        Files.writeString(module.resolve("service.Dockerfile"), "FROM openjdk:17\n");

        Path module2 = createModule(root, "web");
        writeJavaFile(module2, "com/example", "Web.java",
                "package com.example;\n@SpringBootApplication\npublic class Web {}");

        List<DetectedService> result = detector.detectServicesWithConfidence(root);

        DetectedService svc = result.stream()
                .filter(s -> s.path().getFileName().toString().equals("svc"))
                .findFirst().orElseThrow();
        assertEquals(DetectionConfidence.MEDIUM, svc.confidence());
    }

    @Test
    void detectsMainMethod(@TempDir Path root) throws IOException {
        Path module = createModule(root, "batch-processor");
        writeJavaFile(module, "com/example", "BatchRunner.java",
                """
                package com.example;
                public class BatchRunner {
                    public static void main(String[] args) {
                        System.out.println("running");
                    }
                }
                """);

        Path module2 = createModule(root, "api");
        writeJavaFile(module2, "com/example", "Api.java",
                "package com.example;\n@SpringBootApplication\npublic class Api {}");

        List<DetectedService> result = detector.detectServicesWithConfidence(root);

        DetectedService batch = result.stream()
                .filter(s -> s.path().getFileName().toString().equals("batch-processor"))
                .findFirst().orElseThrow();
        assertEquals(DetectionConfidence.LOW, batch.confidence());
        assertEquals("main-method", batch.signal());
    }


    @Test
    void excludesSharedLibrary(@TempDir Path root) throws IOException {
        Path service = createModule(root, "user-service");
        writeJavaFile(service, "com/example", "UserApp.java",
                "package com.example;\n@SpringBootApplication\npublic class UserApp {}");

        Path lib = createModule(root, "common-lib");
        writeJavaFile(lib, "com/example/common", "Utils.java",
                "package com.example.common;\npublic class Utils { public static String format(String s) { return s; } }");

        List<DetectedService> result = detector.detectServicesWithConfidence(root);

        assertEquals(1, result.size());
        assertEquals("user-service", result.get(0).path().getFileName().toString());
    }

    @Test
    void excludesRootAggregatorPom(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("pom.xml"),
                "<project><modules><module>svc-a</module></modules></project>");

        Path svcA = createModule(root, "svc-a");
        writeJavaFile(svcA, "com/example", "SvcA.java",
                "package com.example;\n@SpringBootApplication\npublic class SvcA {}");

        List<DetectedService> result = detector.detectServicesWithConfidence(root);

        assertEquals(1, result.size());
        assertEquals("svc-a", result.get(0).path().getFileName().toString());
    }

    @Test
    void excludesNestedAggregatorPom(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("pom.xml"),
                "<project><modules><module>services</module><module>app</module></modules></project>");

        Path services = root.resolve("services");
        Files.createDirectories(services);
        Files.writeString(services.resolve("pom.xml"),
                "<project><modules><module>user-service</module></modules></project>");

        Path userSvc = createModule(services, "user-service");
        writeJavaFile(userSvc, "com/example", "UserApp.java",
                "package com.example;\n@SpringBootApplication\npublic class UserApp {}");

        Path app = createModule(root, "app");
        writeJavaFile(app, "com/example", "App.java",
                "package com.example;\n@SpringBootApplication\npublic class App {}");

        List<DetectedService> result = detector.detectServicesWithConfidence(root);

        List<String> names = result.stream().map(s -> s.path().getFileName().toString()).sorted().toList();
        assertEquals(List.of("app", "user-service"), names);
    }

    @Test
    void doesNotExcludePaymentProcessor(@TempDir Path root) throws IOException {
        Path svc = createModule(root, "payment-processor");
        writeJavaFile(svc, "com/example", "PaymentApp.java",
                "package com.example;\n@SpringBootApplication\npublic class PaymentApp {}");

        List<DetectedService> result = detector.detectServicesWithConfidence(root);

        assertEquals(1, result.size());
        assertEquals("payment-processor", result.get(0).path().getFileName().toString());
    }

    @Test
    void doesNotExcludeInteriorKeywordComponent(@TempDir Path root) throws IOException {
        Path svc1 = createModule(root, "test-gateway");
        writeJavaFile(svc1, "com/example", "GatewayApp.java",
                "package com.example;\n@SpringBootApplication\npublic class GatewayApp {}");

        Path svc2 = createModule(root, "platform-api");
        writeJavaFile(svc2, "com/example", "PlatformApp.java",
                "package com.example;\n@SpringBootApplication\npublic class PlatformApp {}");

        List<DetectedService> result = detector.detectServicesWithConfidence(root);

        List<String> names = result.stream().map(s -> s.path().getFileName().toString()).sorted().toList();
        assertEquals(List.of("platform-api", "test-gateway"), names);
    }

    @Test
    void excludesSuffixKeywordComponent(@TempDir Path root) throws IOException {
        Path svc = createModule(root, "api-service");
        writeJavaFile(svc, "com/example", "ApiApp.java",
                "package com.example;\n@SpringBootApplication\npublic class ApiApp {}");

        Path excluded = createModule(root, "activiti-api-example");
        writeJavaFile(excluded, "com/example", "ExApp.java",
                "package com.example;\n@SpringBootApplication\npublic class ExApp {}");

        List<DetectedService> result = detector.detectServicesWithConfidence(root);

        assertEquals(1, result.size());
        assertEquals("api-service", result.get(0).path().getFileName().toString());
    }

    @Test
    void excludesTestModuleByKeyword(@TempDir Path root) throws IOException {
        Path svc = createModule(root, "api-service");
        writeJavaFile(svc, "com/example", "ApiApp.java",
                "package com.example;\n@SpringBootApplication\npublic class ApiApp {}");

        Path testMod = createModule(root, "integration-tests");
        writeJavaFile(testMod, "com/example", "TestRunner.java",
                "package com.example;\npublic class TestRunner { public static void main(String[] args) {} }");

        List<DetectedService> result = detector.detectServicesWithConfidence(root);

        assertEquals(1, result.size());
        assertEquals("api-service", result.get(0).path().getFileName().toString());
    }

    @Test
    void singleModuleFallback(@TempDir Path root) throws IOException {
        Path module = createModule(root, "mystery-app");
        writeJavaFile(module, "com/example", "Config.java",
                "package com.example;\npublic class Config {}");

        List<DetectedService> result = detector.detectServicesWithConfidence(root);

        assertEquals(1, result.size());
        assertEquals(DetectionConfidence.LOW, result.get(0).confidence());
        assertEquals("single-module-fallback", result.get(0).signal());
    }

    @Test
    void allGatedFallback(@TempDir Path root) throws IOException {
        Path lib1 = createModule(root, "common-api");
        writeJavaFile(lib1, "com/example", "Api.java",
                "package com.example;\npublic interface Api {}");

        Path lib2 = createModule(root, "common-model");
        writeJavaFile(lib2, "com/example", "Model.java",
                "package com.example;\npublic class Model {}");

        List<DetectedService> result = detector.detectServicesWithConfidence(root);

        assertEquals(1, result.size());
        assertEquals(root, result.get(0).path());
        assertEquals("all-gated-fallback", result.get(0).signal());
    }

    @Test
    void noCandidatesFallback(@TempDir Path root) throws IOException {
        List<DetectedService> result = detector.detectServicesWithConfidence(root);

        assertEquals(1, result.size());
        assertEquals(root, result.get(0).path());
        assertEquals("no-candidates-fallback", result.get(0).signal());
    }

    @Test
    void frameworkTakesPriorityOverDockerfile(@TempDir Path root) throws IOException {
        Path module = createModule(root, "full-service");
        Files.writeString(module.resolve("Dockerfile"), "FROM openjdk:17\n");
        writeJavaFile(module, "com/example", "FullApp.java",
                "package com.example;\n@SpringBootApplication\npublic class FullApp { public static void main(String[] args) {} }");

        List<DetectedService> result = detector.detectServicesWithConfidence(root);

        assertEquals(1, result.size());
        assertEquals(DetectionConfidence.HIGH, result.get(0).confidence());
        assertEquals("framework-entry-point", result.get(0).signal());
    }

    private Path createModule(Path root, String name) throws IOException {
        Path module = root.resolve(name);
        Files.createDirectories(module.resolve("src/main/java"));
        Files.writeString(module.resolve("pom.xml"),
                "<project><artifactId>" + name + "</artifactId></project>");
        return module;
    }

    private void writeJavaFile(Path module, String pkg, String filename, String content) throws IOException {
        Path dir = module.resolve("src/main/java").resolve(pkg);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(filename), content);
    }
}

