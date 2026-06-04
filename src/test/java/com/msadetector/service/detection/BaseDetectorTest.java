package com.msadetector.service.detection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msadetector.entity.AnalysisJob;
import com.msadetector.entity.DetectedAntiPattern;
import com.msadetector.entity.Microservice;
import com.msadetector.entity.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BaseDetectorTest {

    private TestDetector detector;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        detector = new TestDetector(new ObjectMapper());
    }

    static class TestDetector extends BaseDetector {
        TestDetector(ObjectMapper objectMapper) {
            super(objectMapper);
        }

        @Override
        public List<DetectedAntiPattern> detect(Project project, List<Microservice> microservices, AnalysisJob job) {
            return List.of();
        }

        public String publicToJson(Object obj) { return toJson(obj); }
        public String publicTruncate(String str, int max) { return truncate(str, max); }
        public Map<String, Object> publicReadSnippet(Path p, int line, int ctx) { return readSnippet(p, line, ctx); }
        public Map<String, Object> publicBuildSnippet(String f, int line, String code) { return buildSnippet(f, line, code); }
        public String publicSnippetsToJson(List<Map<String, Object>> s) { return snippetsToJson(s); }
    }

    @Test
    void toJson_serializesObject() {
        String json = detector.publicToJson(List.of("a", "b"));
        assertEquals("[\"a\",\"b\"]", json);
    }

    @Test
    void toJson_onError_returnsEmptyArray() {
        TestDetector badDetector = new TestDetector(new ObjectMapper() {
            @Override
            public String writeValueAsString(Object value) throws com.fasterxml.jackson.core.JsonProcessingException {
                throw new com.fasterxml.jackson.core.JsonProcessingException("fail") {};
            }
        });
        assertEquals("[]", badDetector.publicToJson(List.of("a")));
    }

    @Test
    void truncate_shortString_unchanged() {
        assertEquals("hello", detector.publicTruncate("hello", 10));
    }

    @Test
    void truncate_longString_truncated() {
        String result = detector.publicTruncate("hello world", 5);
        assertEquals("hello...", result);
    }

    @Test
    void truncate_null_returnsNull() {
        assertNull(detector.publicTruncate(null, 10));
    }

    @Test
    void readSnippet_validFile_returnsSnippet() throws Exception {
        Path file = tempDir.resolve("Test.java");
        Files.writeString(file, "line1\nline2\nline3\nline4\nline5\nline6\nline7\n");

        Map<String, Object> snippet = detector.publicReadSnippet(file, 4, 2);

        assertNotNull(snippet);
        assertEquals(2, snippet.get("startLine"));
        assertEquals(6, snippet.get("endLine"));
        assertEquals(4, snippet.get("highlightLine"));
        assertTrue(snippet.get("snippet").toString().contains("line4"));
    }

    @Test
    void readSnippet_invalidLine_returnsNull() throws Exception {
        Path file = tempDir.resolve("Test.java");
        Files.writeString(file, "line1\n");

        assertNull(detector.publicReadSnippet(file, 0, 2));
    }

    @Test
    void readSnippet_nonexistentFile_returnsNull() {
        assertNull(detector.publicReadSnippet(tempDir.resolve("nope.java"), 1, 2));
    }

    @Test
    void readSnippet_nullPath_returnsNull() {
        assertNull(detector.publicReadSnippet(null, 1, 2));
    }

    @Test
    void buildSnippet_validCode_returnsMap() {
        Map<String, Object> snippet = detector.publicBuildSnippet("Foo.java", 10, "public void foo() {}");

        assertNotNull(snippet);
        assertEquals("Foo.java", snippet.get("file"));
        assertEquals(10, snippet.get("startLine"));
        assertEquals(10, snippet.get("highlightLine"));
    }

    @Test
    void buildSnippet_blankCode_returnsNull() {
        assertNull(detector.publicBuildSnippet("Foo.java", 1, ""));
        assertNull(detector.publicBuildSnippet("Foo.java", 1, "  "));
        assertNull(detector.publicBuildSnippet("Foo.java", 1, null));
    }

    @Test
    void snippetsToJson_filtersNulls() {
        Map<String, Object> valid = Map.of("file", "a.java", "startLine", 1);
        String json = detector.publicSnippetsToJson(Arrays.asList(valid, null, valid));

        assertNotNull(json);
        assertTrue(json.contains("a.java"));
    }

    @Test
    void snippetsToJson_allNulls_returnsNull() {
        assertNull(detector.publicSnippetsToJson(List.of()));
    }

    @Test
    void buildSnippet_backslashNormalized() {
        Map<String, Object> snippet = detector.publicBuildSnippet("src\\main\\Foo.java", 1, "code");
        assertEquals("src/main/Foo.java", snippet.get("file"));
    }
}

