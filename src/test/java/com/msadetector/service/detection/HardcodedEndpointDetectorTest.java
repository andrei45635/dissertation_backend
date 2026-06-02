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

class HardcodedEndpointDetectorTest {

    private HardcodedEndpointDetector detector;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        detector = new HardcodedEndpointDetector(new ObjectMapper(),
                "http://,https://,localhost:,127.0.0.1");
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

    private Path createJavaFile(String serviceName, String fileName, String content) throws Exception {
        Path srcDir = tempDir.resolve(serviceName + "/src/main/java");
        Files.createDirectories(srcDir);
        Path file = srcDir.resolve(fileName);
        Files.writeString(file, content);
        return file;
    }

    @Test
    void detect_hardcodedHttp_flagsPattern() throws Exception {
        createJavaFile("svc", "Client.java",
                "public class Client {\n" +
                "    String url = \"http://payment-service:8080/api/pay\";\n" +
                "}\n");

        Project project = createProject();
        Microservice ms = createMs("svc");

        List<DetectedAntiPattern> results = detector.detect(project, List.of(ms));

        assertEquals(1, results.size());
        DetectedAntiPattern pattern = results.getFirst();
        assertEquals(AntiPatternType.HARDCODED_ENDPOINTS, pattern.getPatternType());
        assertEquals(Severity.MEDIUM, pattern.getSeverity());
        assertTrue(pattern.getDescription().contains("svc"));
        assertTrue(pattern.getDescription().contains("1 hardcoded"));
    }

    @Test
    void detect_localhost_flagsPattern() throws Exception {
        createJavaFile("svc", "Config.java",
                "public class Config {\n" +
                "    String url = \"localhost:8080\";\n" +
                "}\n");

        Project project = createProject();
        List<DetectedAntiPattern> results = detector.detect(project, List.of(createMs("svc")));

        assertEquals(1, results.size());
    }

    @Test
    void detect_noHardcodedUrls_returnsEmpty() throws Exception {
        createJavaFile("svc", "Service.java",
                "public class Service {\n" +
                "    private final RestTemplate restTemplate;\n" +
                "    public String call() { return restTemplate.getForObject(serviceUrl, String.class); }\n" +
                "}\n");

        Project project = createProject();
        List<DetectedAntiPattern> results = detector.detect(project, List.of(createMs("svc")));

        assertTrue(results.isEmpty());
    }

    @Test
    void detect_commentsIgnored() throws Exception {
        createJavaFile("svc", "Service.java",
                "public class Service {\n" +
                "    // http://example.com/api\n" +
                "    /* http://example.com/api */\n" +
                "    * http://example.com/api\n" +
                "}\n");

        Project project = createProject();
        List<DetectedAntiPattern> results = detector.detect(project, List.of(createMs("svc")));

        assertTrue(results.isEmpty());
    }

    @Test
    void detect_importsIgnored() throws Exception {
        createJavaFile("svc", "Service.java",
                "package com.example;\n" +
                "import org.springframework.http.HttpStatus;\n" +
                "public class Service {}\n");

        Project project = createProject();
        List<DetectedAntiPattern> results = detector.detect(project, List.of(createMs("svc")));

        assertTrue(results.isEmpty());
    }

    @Test
    void detect_multipleHardcodedUrls_countsAll() throws Exception {
        createJavaFile("svc", "Client.java",
                "public class Client {\n" +
                "    String url1 = \"http://svc-a:8080/api\";\n" +
                "    String url2 = \"http://svc-b:8081/api\";\n" +
                "    String url3 = \"https://external.com/webhook\";\n" +
                "}\n");

        Project project = createProject();
        List<DetectedAntiPattern> results = detector.detect(project, List.of(createMs("svc")));

        assertEquals(1, results.size());
        assertTrue(results.getFirst().getDescription().contains("3 hardcoded"));
    }

    @Test
    void detect_noSrcDir_returnsEmpty() {
        Project project = createProject();
        List<DetectedAntiPattern> results = detector.detect(project, List.of(createMs("no-src")));

        assertTrue(results.isEmpty());
    }

    @Test
    void detect_127001_flagsPattern() throws Exception {
        createJavaFile("svc", "Config.java",
                "public class Config {\n" +
                "    String url = \"127.0.0.1:9090\";\n" +
                "}\n");

        Project project = createProject();
        List<DetectedAntiPattern> results = detector.detect(project, List.of(createMs("svc")));

        assertEquals(1, results.size());
    }
}

