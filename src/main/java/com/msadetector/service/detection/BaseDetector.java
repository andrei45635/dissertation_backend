package com.msadetector.service.detection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msadetector.entity.CodeSmell;
import com.msadetector.entity.Microservice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Base class providing shared utility methods for all anti-pattern detectors.
 */
public abstract class BaseDetector implements AntiPatternDetector {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final ObjectMapper objectMapper;

    /** Default number of context lines above/below the target line. */
    protected static final int DEFAULT_CONTEXT_LINES = 3;

    protected BaseDetector(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    protected String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }

    protected String truncate(String str, int maxLength) {
        if (str == null) return null;
        return str.length() > maxLength ? str.substring(0, maxLength) + "..." : str;
    }


    /**
     * Reads a code snippet from a file centred on the given line number,
     * including {@code contextLines} lines above and below.
     *
     * @param filePath     absolute or relative path to the source file
     * @param lineNumber   1-based line where the issue was detected
     * @param contextLines number of surrounding context lines
     * @return a map suitable for JSON serialization, or {@code null} on failure
     */
    protected Map<String, Object> readSnippet(Path filePath, int lineNumber, int contextLines) {
        if (filePath == null || !Files.exists(filePath) || lineNumber < 1) {
            return null;
        }
        try {
            List<String> allLines = Files.readAllLines(filePath, java.nio.charset.StandardCharsets.ISO_8859_1);
            int startLine = Math.max(1, lineNumber - contextLines);
            int endLine = Math.min(allLines.size(), lineNumber + contextLines);

            StringBuilder sb = new StringBuilder();
            for (int i = startLine; i <= endLine; i++) {
                sb.append(allLines.get(i - 1));
                if (i < endLine) sb.append('\n');
            }

            return Map.of(
                    "file", filePath.toString().replace('\\', '/'),
                    "startLine", startLine,
                    "endLine", endLine,
                    "highlightLine", lineNumber,
                    "snippet", sb.toString()
            );
        } catch (IOException e) {
            log.debug("Could not read snippet from {}: {}", filePath, e.getMessage());
            return null;
        }
    }

    /**
     * Convenience overload using the default context window.
     */
    protected Map<String, Object> readSnippet(Path filePath, int lineNumber) {
        return readSnippet(filePath, lineNumber, DEFAULT_CONTEXT_LINES);
    }

    /**
     * Creates a snippet map from an already-known code string (no file read).
     * Useful when the code is already available (e.g. from Spoon or a prior scan).
     */
    protected Map<String, Object> buildSnippet(String file, int lineNumber, String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        long lineCount = code.chars().filter(ch -> ch == '\n').count() + 1;
        return Map.of(
                "file", file != null ? file.replace('\\', '/') : "unknown",
                "startLine", lineNumber,
                "endLine", lineNumber + (int) lineCount - 1,
                "highlightLine", lineNumber,
                "snippet", code
        );
    }

    /** Source root used to resolve a code smell's class to an on-disk file. */
    private static final String SRC_MAIN_JAVA = "src/main/java";

    /**
     * Resolves a code snippet for a DesigniteJava code smell. DesigniteJava reports
     * the package name (not an actual file path) and no line number, so this attempts,
     * in order: a direct path, the class name resolved as a path under the service's
     * source root, and finally the package name combined with the simple class name.
     * Falls back to a description-only snippet when the source file cannot be located.
     *
     * @param smell       the code smell to resolve
     * @param projectRoot the project root path
     * @param ms          the microservice the smell belongs to
     * @return a snippet map suitable for JSON serialization
     */
    protected Map<String, Object> resolveSmellSnippet(CodeSmell smell, Path projectRoot, Microservice ms) {
        if (smell.getFilePath() != null) {
            Path directPath = projectRoot.resolve(smell.getFilePath());
            if (Files.exists(directPath)) {
                int line = smell.getLineNumber() != null ? smell.getLineNumber() : 1;
                return readSnippet(directPath, line, 5);
            }
        }

        if (smell.getClassName() != null) {
            Path serviceSrc = projectRoot.resolve(ms.getRelativePath()).resolve(SRC_MAIN_JAVA);
            String classFile = smell.getClassName().replace('.', '/') + ".java";
            Path resolved = serviceSrc.resolve(classFile);
            if (Files.exists(resolved)) {
                return readSnippet(resolved, 1, 10);
            }
            if (smell.getFilePath() != null && !smell.getClassName().contains(".")) {
                String qualifiedPath = smell.getFilePath().replace('.', '/')
                        + "/" + smell.getClassName() + ".java";
                resolved = serviceSrc.resolve(qualifiedPath);
                if (Files.exists(resolved)) {
                    return readSnippet(resolved, 1, 10);
                }
            }
        }

        return buildSnippet(smell.getClassName(), 0, smell.getDescription());
    }

    /**
     * Converts a list of snippet maps to a JSON string ready to persist.
     */
    protected String snippetsToJson(List<Map<String, Object>> snippets) {
        List<Map<String, Object>> nonNull = snippets.stream()
                .filter(java.util.Objects::nonNull)
                .toList();
        if (nonNull.isEmpty()) return null;
        return toJson(nonNull);
    }
}

