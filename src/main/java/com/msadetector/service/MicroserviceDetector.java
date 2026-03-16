package com.msadetector.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class MicroserviceDetector {

    /**
     * Keywords that indicate non-service modules (examples, samples, demos,
     * documentation, test harnesses, build support, etc.). A directory is
     * excluded when its name equals one of these, or contains one as a
     * hyphen-/dot-separated component (e.g. "http-server-tck" matches "tck").
     */
    private static final Set<String> EXCLUDED_DIR_KEYWORDS = Set.of(
            // examples / samples / demos
            "example", "examples",
            "sample", "samples",
            "demo", "demos",
            // test modules
            "test", "tests",
            "integration-test", "integration-tests",
            "e2e", "e2e-tests",
            "it-tests",
            "tck",
            "testkit",
            // documentation
            "doc", "docs", "documentation",
            "tutorial", "tutorials",
            // benchmarks
            "benchmark", "benchmarks",
            // build support / code generation
            "buildsrc",
            "buildscript",
            "processor",
            // templates / archetypes / starters
            "starter", "starters",
            "template", "templates",
            "archetype", "archetypes",
            // test doubles
            "mock", "mocks",
            "fixture", "fixtures",
            // BOM / dependency management (no runnable code)
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

    public List<Path> detectServices(Path projectRoot) {
        List<Path> services = new ArrayList<>();

        List<Path> pomFiles = findBuildFiles(projectRoot, "pom.xml");
        List<Path> gradleFiles = findBuildFiles(projectRoot, "build.gradle");
        List<Path> gradleKtsFiles = findBuildFiles(projectRoot, "build.gradle.kts");

        for (Path pomFile : pomFiles) {
            Path serviceDir = pomFile.getParent();
            if (isServiceDirectory(serviceDir, projectRoot)) {
                services.add(serviceDir);
            }
        }

        for (Path gradleFile : gradleFiles) {
            Path serviceDir = gradleFile.getParent();
            if (isServiceDirectory(serviceDir, projectRoot) && !services.contains(serviceDir)) {
                services.add(serviceDir);
            }
        }

        for (Path gradleFile : gradleKtsFiles) {
            Path serviceDir = gradleFile.getParent();
            if (isServiceDirectory(serviceDir, projectRoot) && !services.contains(serviceDir)) {
                services.add(serviceDir);
            }
        }

        if (services.isEmpty()) {
            services.add(projectRoot);
        }

        return services;
    }

    private List<Path> findBuildFiles(Path root, String filename) {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk
                    .filter(p -> p.getFileName().toString().equals(filename))
                    .filter(p -> !containsPathSegment(p, root, "target"))
                    .filter(p -> !containsPathSegment(p, root, "build"))
                    .filter(p -> !containsPathSegment(p, root, "node_modules"))
                    .filter(p -> !containsPathSegment(p, root, ".gradle"))
                    .filter(p -> !containsPathSegment(p, root, ".git"))
                    .filter(p -> !containsExcludedSegment(p, root))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /**
     * Checks whether any directory segment in the relative path from root to file
     * exactly matches the given name. This avoids false positives like filtering out
     * "build.gradle" when we only want to exclude the "build/" output directory.
     */
    private boolean containsPathSegment(Path file, Path root, String segment) {
        Path relative = root.relativize(file.getParent()); // parent dirs only, not the filename
        for (Path part : relative) {
            if (part.toString().equals(segment)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if any directory segment in the relative path from root to file
     * matches an excluded keyword (case-insensitive, splitting on hyphens/dots).
     */
    private boolean containsExcludedSegment(Path file, Path root) {
        Path relative = root.relativize(file.getParent());
        for (Path part : relative) {
            if (isExcludedName(part.toString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether a directory name matches an excluded keyword.
     * Matches if the full name equals a keyword, if any hyphen-/dot-separated
     * component equals a keyword (e.g. "activiti-api-basic-task-example" matches
     * "example"), or if the name starts with an excluded prefix
     * (e.g. "module-info-runtime" matches prefix "module-info").
     */
    private boolean isExcludedName(String name) {
        String lower = name.toLowerCase();
        if (EXCLUDED_DIR_KEYWORDS.contains(lower)) {
            return true;
        }
        for (String part : lower.split("[-.]")) {
            if (EXCLUDED_DIR_KEYWORDS.contains(part)) {
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

    private boolean isServiceDirectory(Path dir, Path projectRoot) {
        if (dir.equals(projectRoot)) {
            return !hasSubmodules(dir);
        }

        // Exclude directories whose name indicates they are not real services
        if (isExcludedName(dir.getFileName().toString())) {
            return false;
        }

        boolean hasSrcDir = Files.exists(dir.resolve("src"));
        boolean hasJavaFiles = hasJavaFiles(dir);

        return hasSrcDir || hasJavaFiles;
    }

    private boolean hasSubmodules(Path dir) {
        // Maven multi-module check
        Path pomFile = dir.resolve("pom.xml");
        if (Files.exists(pomFile)) {
            try {
                String content = Files.readString(pomFile, java.nio.charset.StandardCharsets.ISO_8859_1);
                if (content.contains("<modules>") || content.contains("<module>")) {
                    return true;
                }
            } catch (IOException e) {
                // fall through
            }
        }

        // Gradle multi-module check (settings.gradle or settings.gradle.kts)
        for (String settingsFile : List.of("settings.gradle", "settings.gradle.kts")) {
            Path settings = dir.resolve(settingsFile);
            if (Files.exists(settings)) {
                try {
                    String content = Files.readString(settings, java.nio.charset.StandardCharsets.ISO_8859_1);
                    if (content.contains("include")) {
                        return true;
                    }
                } catch (IOException e) {
                    // fall through
                }
            }
        }

        return false;
    }

    private boolean hasJavaFiles(Path dir) {
        try (Stream<Path> walk = Files.walk(dir, 3)) {
            return walk.anyMatch(p -> p.toString().endsWith(".java"));
        } catch (IOException e) {
            return false;
        }
    }
}
