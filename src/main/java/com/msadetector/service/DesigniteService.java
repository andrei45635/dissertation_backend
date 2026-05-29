package com.msadetector.service;

import com.msadetector.entity.CodeSmell;
import com.msadetector.entity.Microservice;
import com.msadetector.enums.Severity;
import com.msadetector.repository.CodeSmellRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Service
public class DesigniteService {

    private static final Logger log = LoggerFactory.getLogger(DesigniteService.class);

    private final CodeSmellRepository codeSmellRepository;
    private final String designiteJarPath;
    private final int timeoutSeconds;

    public DesigniteService(
            CodeSmellRepository codeSmellRepository,
            @Value("${app.analysis.designite-jar-path}") String designiteJarPath,
            @Value("${app.analysis.analysis-timeout-seconds:1800}") int timeoutSeconds
    ) {
        this.codeSmellRepository = codeSmellRepository;
        this.designiteJarPath = designiteJarPath;
        this.timeoutSeconds = timeoutSeconds;
    }

    public void analyzeService(Microservice microservice, Path servicePath) {
        if (!Files.exists(Path.of(designiteJarPath))) {
            log.warn("DesigniteJava not found at {}, skipping analysis", designiteJarPath);
            return;
        }

        try {
            Path outputDir = Files.createTempDirectory("designite-output-");

            boolean finished = runDesignite(servicePath, outputDir);

            if (finished) {
                List<CodeSmell> smells = parseResults(outputDir, microservice);
                codeSmellRepository.saveAll(smells);

                microservice.setNumberOfClasses(countClasses(outputDir));

                log.info("DesigniteJava: parsed and saved {} code smell(s) for service '{}'",
                        smells.size(), microservice.getName());
            }

            deleteDirectory(outputDir);

        } catch (Exception e) {
            log.error("Failed to run DesigniteJava on {}", servicePath, e);
        }
    }

    private boolean runDesignite(Path inputPath, Path outputPath) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                "java", "-jar", designiteJarPath,
                "-i", inputPath.toString(),
                "-o", outputPath.toString()
        );

        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("Designite: {}", line);
            }
        }

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

        if (!finished) {
            process.destroyForcibly();
            log.warn("DesigniteJava timed out after {} seconds", timeoutSeconds);
            return false;
        }

        // DesigniteJava frequently writes valid CSV output while still returning a
        // non-zero exit code, so we do NOT gate parsing on the exit value — we only
        // require that the process actually completed. A non-zero code is logged.
        int exitCode = process.exitValue();
        if (exitCode != 0) {
            log.warn("DesigniteJava exited with code {} for input {}; will still attempt to parse any output produced",
                    exitCode, inputPath);
        }
        return true;
    }

    private List<CodeSmell> parseResults(Path outputDir, Microservice microservice) {
        List<CodeSmell> smells = new ArrayList<>();

        smells.addAll(parseDesignSmells(outputDir.resolve("DesignSmells.csv"), microservice));
        smells.addAll(parseImplementationSmells(outputDir.resolve("ImplementationSmells.csv"), microservice));
        smells.addAll(parseArchitectureSmells(outputDir.resolve("ArchitectureSmells.csv"), microservice));

        return smells;
    }

    private List<CodeSmell> parseDesignSmells(Path csvFile, Microservice microservice) {
        return parseCsvFile(csvFile, microservice, "Design");
    }

    private List<CodeSmell> parseImplementationSmells(Path csvFile, Microservice microservice) {
        return parseCsvFile(csvFile, microservice, "Implementation");
    }

    private List<CodeSmell> parseArchitectureSmells(Path csvFile, Microservice microservice) {
        return parseCsvFile(csvFile, microservice, "Architecture");
    }

    private List<CodeSmell> parseCsvFile(Path csvFile, Microservice microservice, String category) {
        List<CodeSmell> smells = new ArrayList<>();

        Path resolved = locateCsv(csvFile);
        if (resolved == null) {
            log.debug("DesigniteJava CSV not found: {}", csvFile.getFileName());
            return smells;
        }

        try {
            List<String> lines = Files.readAllLines(resolved, java.nio.charset.StandardCharsets.ISO_8859_1);
            if (lines.isEmpty()) {
                return smells;
            }

            // Column positions differ between the three reports (ImplementationSmells.csv
            // has an extra "Method Name" column, ArchitectureSmells.csv has no "Type Name"),
            // so we resolve them from the header rather than hard-coding indices.
            String[] header = parseCsvLine(lines.get(0));
            int smellIdx = indexOfSmellColumn(header);
            if (smellIdx < 0) {
                log.warn("Could not locate a smell-type column in {} (header: {})",
                        resolved.getFileName(), String.join(",", header));
                return smells;
            }
            int classIdx = indexOfColumn(header, "Type Name");
            int pkgIdx = indexOfColumn(header, "Package Name");
            if (classIdx < 0) {
                classIdx = pkgIdx; // ArchitectureSmells.csv has no Type Name column
            }

            for (int i = 1; i < lines.size(); i++) {
                String[] parts = parseCsvLine(lines.get(i));
                if (parts.length <= smellIdx) {
                    continue;
                }

                String smellType = parts[smellIdx].trim();
                if (smellType.isEmpty()) {
                    continue;
                }

                CodeSmell smell = CodeSmell.builder()
                        .microservice(microservice)
                        .smellType(smellType)
                        .className(classIdx >= 0 && classIdx < parts.length ? parts[classIdx].trim() : null)
                        .filePath(pkgIdx >= 0 && pkgIdx < parts.length ? parts[pkgIdx].trim() : null)
                        .severity(mapSeverity(smellType))
                        .sourceTool("DesigniteJava")
                        .description(category + " smell: " + smellType)
                        .build();

                smells.add(smell);
            }
        } catch (IOException e) {
            log.error("Failed to parse CSV file: {}", resolved, e);
        }

        return smells;
    }

    /**
     * Resolves a DesigniteJava output CSV. Tries the expected path first, then falls
     * back to a recursive search under the output directory, since some DesigniteJava
     * versions nest the reports in a sub-folder.
     */
    private Path locateCsv(Path expected) {
        if (Files.exists(expected)) {
            return expected;
        }
        Path dir = expected.getParent();
        String name = expected.getFileName().toString();
        if (dir == null || !Files.exists(dir)) {
            return null;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase(name))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    /** Index of the column holding the smell name (e.g. "Design Smell"), excluding the "Cause" column. */
    private int indexOfSmellColumn(String[] header) {
        for (int i = 0; i < header.length; i++) {
            String h = header[i].trim().toLowerCase();
            if (h.contains("smell") && !h.contains("cause")) {
                return i;
            }
        }
        return -1;
    }

    /** Index of a header column matching the given name (case-insensitive), or -1. */
    private int indexOfColumn(String[] header, String name) {
        for (int i = 0; i < header.length; i++) {
            if (header[i].trim().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    private String[] parseCsvLine(String line) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                parts.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString());

        return parts.toArray(new String[0]);
    }

    private Severity mapSeverity(String smellType) {
        return switch (smellType.toLowerCase()) {
            case "god class", "cyclic dependency", "feature envy" -> Severity.HIGH;
            case "long method", "long parameter list", "complex method" -> Severity.MEDIUM;
            case "magic number", "empty catch clause" -> Severity.LOW;
            default -> Severity.MEDIUM;
        };
    }

    private int countClasses(Path outputDir) {
        Path typeMetrics = locateCsv(outputDir.resolve("TypeMetrics.csv"));
        if (typeMetrics != null) {
            try {
                return Math.max(0, (int) Files.lines(typeMetrics, java.nio.charset.StandardCharsets.ISO_8859_1).count() - 1);
            } catch (IOException | UncheckedIOException e) {
                return 0;
            }
        }
        return 0;
    }

    private void deleteDirectory(Path dir) {
        try {
            Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // ignore
                        }
                    });
        } catch (IOException e) {
            // ignore
        }
    }
}
