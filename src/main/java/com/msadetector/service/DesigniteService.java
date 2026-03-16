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

            boolean success = runDesignite(servicePath, outputDir);

            if (success) {
                List<CodeSmell> smells = parseResults(outputDir, microservice);
                codeSmellRepository.saveAll(smells);

                microservice.setNumberOfClasses(countClasses(outputDir));
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

        return process.exitValue() == 0;
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

        if (!Files.exists(csvFile)) {
            return smells;
        }

        try {
            List<String> lines = Files.readAllLines(csvFile, java.nio.charset.StandardCharsets.ISO_8859_1);

            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                String[] parts = parseCsvLine(line);

                if (parts.length >= 4) {
                    CodeSmell smell = CodeSmell.builder()
                            .microservice(microservice)
                            .smellType(parts[3].trim())
                            .className(parts[2].trim())
                            .filePath(parts[1].trim())
                            .severity(mapSeverity(parts[3].trim()))
                            .sourceTool("DesigniteJava")
                            .description(category + " smell: " + parts[3].trim())
                            .build();

                    smells.add(smell);
                }
            }
        } catch (IOException e) {
            log.error("Failed to parse CSV file: {}", csvFile, e);
        }

        return smells;
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
        Path typeMetrics = outputDir.resolve("TypeMetrics.csv");
        if (Files.exists(typeMetrics)) {
            try {
                return (int) Files.lines(typeMetrics, java.nio.charset.StandardCharsets.ISO_8859_1).count() - 1;
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
