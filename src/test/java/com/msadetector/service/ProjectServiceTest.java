package com.msadetector.service;

import com.msadetector.exception.InvalidFileException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectServiceTest {

    @TempDir
    Path workspaceDir;

    @Test
    void extractZip_allowsValidArchive() throws IOException {
        ProjectService service = serviceWithLimits(1024, 10, 5);
        MockMultipartFile zip = zipFile(Map.of(
                "sample-project/pom.xml", "<project/>",
                "sample-project/src/main/java/App.java", "class App {}"
        ));

        Path extracted = extract(service, zip, 1L);

        assertEquals(workspaceDir.resolve("1/sample-project").normalize(), extracted);
        assertTrue(Files.exists(extracted.resolve("pom.xml")));
        assertTrue(Files.exists(extracted.resolve("src/main/java/App.java")));
    }

    @Test
    void extractZip_rejectsPathTraversalAndCleansPartialDirectory() {
        ProjectService service = serviceWithLimits(1024, 10, 5);
        MockMultipartFile zip = zipFile(Map.of(
                "safe.txt", "safe",
                "../evil.txt", "evil"
        ));

        assertThrows(InvalidFileException.class, () -> extract(service, zip, 2L));

        assertFalse(Files.exists(workspaceDir.resolve("2")));
        assertFalse(Files.exists(workspaceDir.resolve("evil.txt")));
    }

    @Test
    void extractZip_rejectsTooManyEntriesAndCleansPartialDirectory() {
        ProjectService service = serviceWithLimits(1024, 1, 5);
        MockMultipartFile zip = zipFile(Map.of(
                "one.txt", "one",
                "two.txt", "two"
        ));

        assertThrows(InvalidFileException.class, () -> extract(service, zip, 3L));

        assertFalse(Files.exists(workspaceDir.resolve("3")));
    }

    @Test
    void extractZip_rejectsTooDeepEntriesAndCleansPartialDirectory() {
        ProjectService service = serviceWithLimits(1024, 10, 2);
        MockMultipartFile zip = zipFile(Map.of(
                "a/b/c/file.txt", "deep"
        ));

        assertThrows(InvalidFileException.class, () -> extract(service, zip, 4L));

        assertFalse(Files.exists(workspaceDir.resolve("4")));
    }

    @Test
    void extractZip_rejectsExcessiveUncompressedSizeAndCleansPartialDirectory() {
        ProjectService service = serviceWithLimits(8, 10, 5);
        MockMultipartFile zip = zipFile(Map.of(
                "large.txt", "123456789"
        ));

        assertThrows(InvalidFileException.class, () -> extract(service, zip, 5L));

        assertFalse(Files.exists(workspaceDir.resolve("5")));
    }

    private ProjectService serviceWithLimits(long maxUncompressedBytes, int maxEntries, int maxDepth) {
        return new ProjectService(
                null,
                null,
                null,
                null,
                null,
                null,
                workspaceDir.toString(),
                maxUncompressedBytes,
                maxEntries,
                maxDepth
        );
    }

    private Path extract(ProjectService service, MockMultipartFile file, Long projectId) {
        return ReflectionTestUtils.invokeMethod(service, "extractZip", file, projectId);
    }

    private MockMultipartFile zipFile(Map<String, String> entries) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(bytes)) {
                for (Map.Entry<String, String> entry : entries.entrySet()) {
                    zos.putNextEntry(new ZipEntry(entry.getKey()));
                    zos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                }
            }
            return new MockMultipartFile("file", "project.zip", "application/zip", bytes.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build test ZIP", e);
        }
    }
}
