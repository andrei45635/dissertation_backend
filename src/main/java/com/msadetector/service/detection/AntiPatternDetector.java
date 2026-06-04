package com.msadetector.service.detection;

import com.msadetector.entity.AnalysisJob;
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
     * @param project       the project being analyzed
     * @param microservices the list of detected microservices
     * @param job           the analysis job the microservices belong to. Used to
     *                      scope dependency and code-smell queries to this run so
     *                      that a re-analysis never mixes data from previous jobs.
     *                      May be {@code null} for callers without a job context
     *                      (e.g. unit tests), in which case detectors fall back to
     *                      project-wide scope.
     * @return a list of detected anti-pattern instances (may be empty)
     */
    List<DetectedAntiPattern> detect(Project project, List<Microservice> microservices, AnalysisJob job);

    /**
     * Convenience overload for callers that have no {@link AnalysisJob} context.
     * Delegates with a {@code null} job, causing detectors to use project-wide scope.
     */
    default List<DetectedAntiPattern> detect(Project project, List<Microservice> microservices) {
        return detect(project, microservices, null);
    }
}
