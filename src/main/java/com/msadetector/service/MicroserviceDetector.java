package com.msadetector.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class MicroserviceDetector {

    private static final Logger log = LoggerFactory.getLogger(MicroserviceDetector.class);

    /**
     * Keywords that indicate non-service modules (examples, samples, demos,
     * documentation, test harnesses, build support, etc.). A directory is
     * excluded when its name equals one of these, or contains one as a
     * hyphen-/dot-separated component (e.g. "http-server-tck" matches "tck").
     */
    private static final Set<String> EXCLUDED_DIR_KEYWORDS = Set.of(
            "example", "examples",
            "sample", "samples",
            "demo", "demos",
            "test", "tests",
            "integration-test", "integration-tests",
            "e2e", "e2e-tests",
            "it-tests",
            "tck",
            "testkit",
            "doc", "docs", "documentation",
            "tutorial", "tutorials",
            "benchmark", "benchmarks",
            "buildsrc",
            "buildscript",
            "starter", "starters",
            "template", "templates",
            "archetype", "archetypes",
            "mock", "mocks",
            "fixture", "fixtures",
            "bom", "dependencies", "platform"
    );

    /**
     * Multi-word prefixes that cannot be matched by single-component keyword
     * splitting. A directory is excluded when its lowercased name starts with
     * any of these prefixes (e.g. "module-info-runtime" starts with "module-info").
     */
    private static final Set<String> EXCLUDED_DIR_PREFIXES = Set.of(
            "module-info"
    );

    /** Annotations / markers that indicate a framework entry point. The
     * {@code extends MicroserviceApplication} marker recognises CDI/Jakarta EE
     * microservices (e.g. SiteWhere) whose bootstrap class is annotated with a
     * generic {@code @ApplicationScoped} and inherits {@code main()} from a shared
     * base class, so neither a Spring annotation nor a literal main() is present
     * in the module itself. */
    private static final Pattern FRAMEWORK_ENTRY_PATTERN = Pattern.compile(
            "@SpringBootApplication|@QuarkusMain|@ApplicationPath|Micronaut\\.run\\(|extends\\s+MicroserviceApplication"
    );

    /** Pattern matching a Java main method signature. */
    private static final Pattern MAIN_METHOD_PATTERN = Pattern.compile(
            "public\\s+static\\s+void\\s+main\\s*\\("
    );

    /** Detection confidence levels assigned to each discovered service based on
     * which deployability signal was matched.
     */
    public enum DetectionConfidence {
        HIGH,
        MEDIUM,
        LOW
    }

    /**
     * Holds the result of detection for a single module, including the directory
     * path, the confidence level, and which signal triggered the match.
     */
    public record DetectedService(Path path, DetectionConfidence confidence, String signal) {}

    /**
     * Detects microservices in the given project root by applying a multi-signal
     * deployability gate to each candidate module. Each candidate is evaluated
     * against three signals in priority order: framework entry point (HIGH),
     * Dockerfile (MEDIUM), and {@code main()} method (LOW). If no candidates
     * pass the gate, a fallback is applied.
     *
     * @param projectRoot the root directory of the project to scan
     * @return a list of detected services with their confidence levels
     */
    public List<DetectedService> detectServicesWithConfidence(Path projectRoot) {
        List<Path> candidates = findCandidateModules(projectRoot);
        List<DetectedService> services = new ArrayList<>();

        for (Path candidate : candidates) {
            DetectedService detected = evaluateDeployabilityGate(candidate);
            if (detected != null) {
                services.add(detected);
            }
        }

        if (services.isEmpty()) {
            if (candidates.size() == 1) {
                Path sole = candidates.get(0);
                log.warn("Deployability gate eliminated the only candidate '{}'; keeping it as fallback", sole.getFileName());
                services.add(new DetectedService(sole, DetectionConfidence.LOW, "single-module-fallback"));
            } else if (candidates.isEmpty()) {
                log.warn("No build-file candidates found; falling back to project root");
                services.add(new DetectedService(projectRoot, DetectionConfidence.LOW, "no-candidates-fallback"));
            } else {
                log.warn("Deployability gate eliminated all {} candidates; falling back to project root", candidates.size());
                services.add(new DetectedService(projectRoot, DetectionConfidence.LOW, "all-gated-fallback"));
            }
        }

        return services;
    }

    /**
     * Returns only the paths of detected services, for backward compatibility.
     *
     * @param projectRoot the root directory of the project to scan
     * @return a list of service directory paths
     */
    public List<Path> detectServices(Path projectRoot) {
        return detectServicesWithConfidence(projectRoot).stream()
                .map(DetectedService::path)
                .toList();
    }

    /**
     * Collects all directories containing a build file, then filters out those
     * matching exclusion keywords and aggregator modules (both root-level and nested).
     *
     * @param projectRoot the root directory of the project to scan
     * @return candidate module directories that passed exclusion and aggregator checks
     */
    private List<Path> findCandidateModules(Path projectRoot) {
        List<Path> allBuildDirs = new ArrayList<>();

        for (String filename : List.of("pom.xml", "build.gradle", "build.gradle.kts")) {
            for (Path buildFile : findBuildFiles(projectRoot, filename)) {
                Path dir = buildFile.getParent();
                if (!allBuildDirs.contains(dir)) {
                    allBuildDirs.add(dir);
                }
            }
        }

        List<Path> candidates = new ArrayList<>();
        for (Path dir : allBuildDirs) {
            if (isExcludedName(dir.getFileName().toString())) {
                log.debug("Excluding '{}': matches exclusion keyword", dir.getFileName());
                continue;
            }
            if (hasExcludedAncestor(dir, projectRoot)) {
                log.debug("Excluding '{}': ancestor directory matches exclusion keyword", dir.getFileName());
                continue;
            }
            if (dir.equals(projectRoot)) {
                if (hasSubmodules(dir)) {
                    log.debug("Excluding project root: it is an aggregator");
                    continue;
                }
            } else {
                if (isAggregatorModule(dir)) {
                    log.debug("Excluding '{}': nested aggregator module", dir.getFileName());
                    continue;
                }
            }
            candidates.add(dir);
        }

        return candidates;
    }

    /**
     * Evaluates the deployability gate for a single candidate module by checking
     * three signals in priority order: framework entry point, Dockerfile presence,
     * and {@code main()} method. Returns the first matching signal as a
     * {@link DetectedService}, or {@code null} if none match.
     *
     * @param dir the candidate module directory
     * @return a {@link DetectedService} if any signal fires, {@code null} otherwise
     */
    private DetectedService evaluateDeployabilityGate(Path dir) {
        if (hasFrameworkEntryPoint(dir)) {
            log.info("Service '{}': framework entry point detected (HIGH confidence)", dir.getFileName());
            return new DetectedService(dir, DetectionConfidence.HIGH, "framework-entry-point");
        }

        if (hasDockerfile(dir)) {
            log.info("Service '{}': Dockerfile detected (MEDIUM confidence)", dir.getFileName());
            return new DetectedService(dir, DetectionConfidence.MEDIUM, "dockerfile");
        }

        if (hasMainMethod(dir)) {
            log.info("Service '{}': main() method detected (LOW confidence)", dir.getFileName());
            return new DetectedService(dir, DetectionConfidence.LOW, "main-method");
        }

        log.debug("Excluding '{}': no deployability signal detected", dir.getFileName());
        return null;
    }

    /**
     * Scans {@code src/main/java} for a class annotated with a known framework
     * startup annotation ({@code @SpringBootApplication}, {@code @QuarkusMain},
     * {@code @ApplicationPath}, or {@code Micronaut.run(...)}). Files named
     * {@code *Application.java} or {@code *Main.java} are checked first as a
     * fast path before falling back to a full directory scan.
     *
     * @param dir the module directory to scan
     * @return {@code true} if a framework entry point was found
     */
    private boolean hasFrameworkEntryPoint(Path dir) {
        Path srcMain = dir.resolve("src/main/java");
        if (!Files.exists(srcMain)) return false;

        try (Stream<Path> files = Files.walk(srcMain)) {
            List<Path> priorityFiles = files
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith("Application.java") || name.endsWith("Main.java");
                    })
                    .toList();

            for (Path file : priorityFiles) {
                if (fileMatchesPattern(file, FRAMEWORK_ENTRY_PATTERN)) {
                    return true;
                }
            }
        } catch (IOException e) {
            log.warn("Failed to scan priority files in {}: {}", dir, e.getMessage());
        }

        try (Stream<Path> files = Files.walk(srcMain)) {
            return files
                    .filter(p -> p.toString().endsWith(".java"))
                    .anyMatch(p -> fileMatchesPattern(p, FRAMEWORK_ENTRY_PATTERN));
        } catch (IOException e) {
            log.warn("Failed to scan for framework entry point in {}: {}", dir, e.getMessage());
            return false;
        }
    }

    /**
     * Checks whether the module directory contains a Dockerfile. Recognises
     * {@code Dockerfile}, {@code dockerfile}, and {@code *.Dockerfile} variants.
     *
     * @param dir the module directory to check
     * @return {@code true} if a Dockerfile is present
     */
    private boolean hasDockerfile(Path dir) {
        if (Files.exists(dir.resolve("Dockerfile")) || Files.exists(dir.resolve("dockerfile"))) {
            return true;
        }
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.anyMatch(p -> p.getFileName().toString().toLowerCase().endsWith(".dockerfile"));
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Scans {@code src/main/java} for a {@code public static void main(String[])}
     * method signature on non-comment lines. Only fires when {@code src/main/}
     * exists, to avoid matching test utilities.
     *
     * @param dir the module directory to scan
     * @return {@code true} if a main method was found
     */
    private boolean hasMainMethod(Path dir) {
        Path srcMain = dir.resolve("src/main/java");
        if (!Files.exists(srcMain)) return false;

        try (Stream<Path> files = Files.walk(srcMain)) {
            return files
                    .filter(p -> p.toString().endsWith(".java"))
                    .anyMatch(p -> fileMatchesPattern(p, MAIN_METHOD_PATTERN));
        } catch (IOException e) {
            log.warn("Failed to scan for main() in {}: {}", dir, e.getMessage());
            return false;
        }
    }

    /**
     * Reads a Java source file line by line and returns {@code true} if any
     * non-comment, non-import line matches the given regex pattern. Block
     * comments, single-line comments, {@code import}, and {@code package}
     * declarations are skipped.
     *
     * @param javaFile the Java source file to scan
     * @param pattern  the regex pattern to search for
     * @return {@code true} if a matching line was found
     */
    private boolean fileMatchesPattern(Path javaFile, Pattern pattern) {
        try {
            List<String> lines = Files.readAllLines(javaFile, java.nio.charset.StandardCharsets.ISO_8859_1);
            boolean inBlockComment = false;
            for (String rawLine : lines) {
                String line = rawLine.trim();

                if (inBlockComment) {
                    if (line.contains("*/")) {
                        inBlockComment = false;
                    }
                    continue;
                }
                if (line.startsWith("/*")) {
                    if (!line.contains("*/")) {
                        inBlockComment = true;
                    }
                    continue;
                }

                if (line.startsWith("//") || line.startsWith("*")
                        || line.startsWith("import ") || line.startsWith("package ")) {
                    continue;
                }

                if (pattern.matcher(line).find()) {
                    return true;
                }
            }
        } catch (IOException e) {
            log.warn("Failed to read file {}: {}", javaFile, e.getMessage());
        }
        return false;
    }

    /**
     * Walks the directory tree from {@code root} looking for files with the given
     * name, excluding output directories ({@code target/}, {@code build/},
     * {@code node_modules/}, {@code .gradle/}, {@code .git/}) and directories
     * matching exclusion keywords.
     *
     * @param root     the root directory to walk
     * @param filename the build file name to search for
     * @return a list of matching build file paths
     */
    private List<Path> findBuildFiles(Path root, String filename) {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk
                    .filter(p -> p.getFileName().toString().equals(filename))
                    .filter(p -> !containsPathSegment(p, root, "target"))
                    .filter(p -> !containsPathSegment(p, root, "build"))
                    .filter(p -> !containsPathSegment(p, root, "node_modules"))
                    .filter(p -> !containsPathSegment(p, root, ".gradle"))
                    .filter(p -> !containsPathSegment(p, root, ".git"))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /**
     * Checks whether any directory segment in the relative path from {@code root}
     * to {@code file} exactly matches the given segment name.
     *
     * @param file    the file whose path to inspect
     * @param root    the root directory to relativize against
     * @param segment the directory name to look for
     * @return {@code true} if a matching segment was found
     */
    private boolean containsPathSegment(Path file, Path root, String segment) {
        Path relative = root.relativize(file.getParent());
        for (Path part : relative) {
            if (part.toString().equals(segment)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if any ancestor directory between {@code dir} and
     * {@code root} has a name that exactly matches an excluded keyword.
     * Unlike {@link #isExcludedName(String)}, this uses only full-name matching
     * (no component splitting) to avoid false positives on ancestor directories
     * like "spring-boot-starter-parent".
     *
     * @param dir  the candidate directory
     * @param root the project root
     * @return {@code true} if an excluded ancestor was found
     */
    private boolean hasExcludedAncestor(Path dir, Path root) {
        Path relative = root.relativize(dir);
        int count = relative.getNameCount();
        for (int i = 0; i < count - 1; i++) {
            String segment = relative.getName(i).toString().toLowerCase();
            if (EXCLUDED_DIR_KEYWORDS.contains(segment)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether a directory name matches an excluded keyword. Matches if the
     * full lowercased name equals a keyword, if the last hyphen- or dot-separated
     * component equals a keyword (e.g. "activiti-api-example" matches "example"),
     * or if the name starts with an excluded prefix.
     *
     * <p>Only the last component is checked to avoid false exclusions of legitimate
     * services like "payment-platform" or "auth-test-service" where an excluded
     * keyword appears as an interior component.</p>
     *
     * @param name the directory name to check
     * @return {@code true} if the name is excluded
     */
    private boolean isExcludedName(String name) {
        String lower = name.toLowerCase();
        if (EXCLUDED_DIR_KEYWORDS.contains(lower)) {
            return true;
        }
        String[] parts = lower.split("[-.]");
        if (parts.length > 1) {
            String lastPart = parts[parts.length - 1];
            if (EXCLUDED_DIR_KEYWORDS.contains(lastPart)) {
                return true;
            }
        }
        for (String prefix : EXCLUDED_DIR_PREFIXES) {
            if (lower.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a directory is a pure aggregator module — one that declares
     * submodules (via {@code <modules>} in a POM or {@code include} in Gradle
     * settings) but has no {@code src/} directory of its own.
     *
     * @param dir the directory to check
     * @return {@code true} if the directory is an aggregator without source
     */
    private boolean isAggregatorModule(Path dir) {
        if (Files.exists(dir.resolve("src"))) {
            return false;
        }
        return hasSubmodules(dir);
    }

    /**
     * Checks whether a directory declares submodules via Maven ({@code <modules>}
     * or {@code <module>} in {@code pom.xml}) or Gradle ({@code include} in
     * {@code settings.gradle} / {@code settings.gradle.kts}).
     *
     * @param dir the directory to check
     * @return {@code true} if submodule declarations were found
     */
    private boolean hasSubmodules(Path dir) {
        Path pomFile = dir.resolve("pom.xml");
        if (Files.exists(pomFile)) {
            try {
                String content = Files.readString(pomFile, java.nio.charset.StandardCharsets.ISO_8859_1);
                if (content.contains("<modules>") || content.contains("<module>")) {
                    return true;
                }
            } catch (IOException e) {
            }
        }

        for (String settingsFile : List.of("settings.gradle", "settings.gradle.kts")) {
            Path settings = dir.resolve(settingsFile);
            if (Files.exists(settings)) {
                try {
                    String content = Files.readString(settings, java.nio.charset.StandardCharsets.ISO_8859_1);
                    if (content.contains("include")) {
                        return true;
                    }
                } catch (IOException e) {
                }
            }
        }

        return false;
    }
}
