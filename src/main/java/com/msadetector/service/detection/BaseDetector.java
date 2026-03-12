package com.msadetector.service.detection;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    // ========================================================================================
    // CODE SNIPPET HELPERS
    // ========================================================================================

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
            List<String> allLines = Files.readAllLines(filePath);
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

