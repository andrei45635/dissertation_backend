package com.msadetector.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msadetector.dto.*;
import com.msadetector.entity.AnalysisJob;
import com.msadetector.entity.AnalysisResult;
import com.msadetector.entity.DetectedAntiPattern;
import com.msadetector.enums.JobStatus;
import com.msadetector.exception.ResourceNotFoundException;
import com.msadetector.repository.AnalysisJobRepository;
import com.msadetector.repository.AnalysisResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
public class JobService {

    private final AnalysisJobRepository jobRepository;
    private final AnalysisResultRepository resultRepository;
    private final ObjectMapper objectMapper;

    public JobService(
            AnalysisJobRepository jobRepository,
            AnalysisResultRepository resultRepository,
            ObjectMapper objectMapper
    ) {
        this.jobRepository = jobRepository;
        this.resultRepository = resultRepository;
        this.objectMapper = objectMapper;
    }

    public AnalysisJobResponse getJobStatus(Long jobId) {
        AnalysisJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + jobId));
        return toJobResponse(job);
    }

    public AnalysisResultResponse getJobResults(Long jobId) {
        AnalysisJob job = jobRepository.findByIdWithResult(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + jobId));

        if (job.getStatus() != JobStatus.COMPLETED) {
            throw new IllegalStateException("Job is not completed yet");
        }

        AnalysisResult result = resultRepository.findByAnalysisJobWithAntiPatterns(job)
                .orElseThrow(() -> new ResourceNotFoundException("Results not found for job: " + jobId));
        if (result == null) {
            throw new ResourceNotFoundException("Results not found for job: " + jobId);
        }

        return toResultResponse(result);
    }

    public List<AnalysisJobResponse> getRecentJobs() {
        return jobRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(20)
                .map(this::toJobResponse)
                .toList();
    }

    @Transactional
    public void cancelJob(Long jobId) {
        AnalysisJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + jobId));

        if (job.getStatus() == JobStatus.COMPLETED || job.getStatus() == JobStatus.FAILED) {
            throw new IllegalStateException("Cannot cancel a finished job");
        }

        job.cancel();
        jobRepository.save(job);
    }

    private AnalysisJobResponse toJobResponse(AnalysisJob job) {
        return new AnalysisJobResponse(
                job.getId(),
                job.getProject().getId(),
                job.getStatus(),
                job.getCurrentPhase(),
                job.getCurrentService(),
                job.getServicesCompleted(),
                job.getTotalServices(),
                job.getProgressPercentage(),
                job.getStartedAt(),
                job.getCompletedAt(),
                job.getErrorMessage()
        );
    }

    private AnalysisResultResponse toResultResponse(AnalysisResult result) {
        List<AntiPatternResponse> antiPatterns = result.getDetectedAntiPatterns().stream()
                .map(this::toAntiPatternResponse)
                .toList();

        DependencyGraphResponse graph = parseDependencyGraph(result.getDependencyGraphJson());

        return new AnalysisResultResponse(
                result.getId(),
                result.getAnalysisJob().getId(),
                result.getHealthScore(),
                result.getServicesAnalyzed(),
                result.getTotalAntiPatterns(),
                result.getTotalCodeSmells(),
                result.getCriticalIssues(),
                result.getHighIssues(),
                result.getMediumIssues(),
                result.getLowIssues(),
                result.getTotalLinesOfCode(),
                result.getAverageServiceSize(),
                result.getCycleCount(),
                antiPatterns,
                graph
        );
    }

    private AntiPatternResponse toAntiPatternResponse(DetectedAntiPattern pattern) {
        List<String> affectedServices = parseJsonList(pattern.getAffectedServicesJson());

        return new AntiPatternResponse(
                pattern.getId(),
                pattern.getPatternType(),
                pattern.getSeverity(),
                pattern.getDescription(),
                affectedServices,
                pattern.getRemediation()
        );
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private DependencyGraphResponse parseDependencyGraph(String json) {
        if (json == null || json.isBlank()) {
            return new DependencyGraphResponse(Collections.emptyList(), Collections.emptyList());
        }
        try {
            return objectMapper.readValue(json, DependencyGraphResponse.class);
        } catch (Exception e) {
            return new DependencyGraphResponse(Collections.emptyList(), Collections.emptyList());
        }
    }
}
