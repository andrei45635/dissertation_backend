package com.msadetector.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Service
public class MicroserviceDetector {

    public List<Path> detectServices(Path projectRoot) {
        List<Path> services = new ArrayList<>();

        List<Path> pomFiles = findBuildFiles(projectRoot, "pom.xml");
        List<Path> gradleFiles = findBuildFiles(projectRoot, "build.gradle");

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

        if (services.isEmpty()) {
            services.add(projectRoot);
        }

        return services;
    }

    private List<Path> findBuildFiles(Path root, String filename) {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk
                    .filter(p -> p.getFileName().toString().equals(filename))
                    .filter(p -> !p.toString().contains("target"))
                    .filter(p -> !p.toString().contains("build"))
                    .filter(p -> !p.toString().contains("node_modules"))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private boolean isServiceDirectory(Path dir, Path projectRoot) {
        if (dir.equals(projectRoot)) {
            return !hasSubmodules(dir);
        }

        boolean hasSrcDir = Files.exists(dir.resolve("src"));
        boolean hasJavaFiles = hasJavaFiles(dir);

        return hasSrcDir || hasJavaFiles;
    }

    private boolean hasSubmodules(Path dir) {
        Path pomFile = dir.resolve("pom.xml");
        if (Files.exists(pomFile)) {
            try {
                String content = Files.readString(pomFile);
                return content.contains("<modules>") || content.contains("<module>");
            } catch (IOException e) {
                return false;
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
