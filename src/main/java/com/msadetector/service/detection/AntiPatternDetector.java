package com.msadetector.service.detection;

import com.msadetector.entity.DetectedAntiPattern;
import com.msadetector.entity.Microservice;
import com.msadetector.entity.Project;

import java.util.List;

/**
 * Common interface for all anti-pattern detectors.
 * Each implementation detects a specific type of microservice anti-pattern.
 * Spring auto-collects all implementations via {@code List<AntiPatternDetector>}.
 */
public interface AntiPatternDetector {

    /**
     * Detects anti-patterns in the given project.
     *
     * @param project        the project being analyzed
     * @param microservices  the list of detected microservices
     * @return a list of detected anti-pattern instances (may be empty)
     */
    List<DetectedAntiPattern> detect(Project project, List<Microservice> microservices);
}

