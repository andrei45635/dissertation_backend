package com.msadetector.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msadetector.dto.*;
import com.msadetector.dto.HealthScoreBreakdownResponse;
import com.msadetector.entity.AnalysisJob;
import com.msadetector.entity.AnalysisResult;
import com.msadetector.entity.DetectedAntiPattern;
import com.msadetector.entity.User;
import com.msadetector.entity.Project;
import com.msadetector.enums.JobStatus;
import com.msadetector.exception.ResourceNotFoundException;
import com.msadetector.repository.AnalysisJobRepository;
import com.msadetector.repository.AnalysisResultRepository;
import com.msadetector.repository.ProjectRepository;
import com.msadetector.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class JobService {

    private final AnalysisJobRepository jobRepository;
    private final AnalysisResultRepository resultRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ObjectMapper objectMapper;
    private final HealthScoreCalculator healthScoreCalculator;
    private final AnalysisDiffService analysisDiffService;

    public JobService(
            AnalysisJobRepository jobRepository,
            AnalysisResultRepository resultRepository,
            UserRepository userRepository,
            ProjectRepository projectRepository,
            ObjectMapper objectMapper,
            HealthScoreCalculator healthScoreCalculator,
            AnalysisDiffService analysisDiffService
    ) {
        this.jobRepository = jobRepository;
        this.resultRepository = resultRepository;
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.objectMapper = objectMapper;
        this.healthScoreCalculator = healthScoreCalculator;
        this.analysisDiffService = analysisDiffService;
    }

    @Transactional(readOnly = true)
    public AnalysisJobResponse getJobStatus(Long jobId, Long userId) {
        User owner = findUser(userId);
        AnalysisJob job = jobRepository.findByIdAndOwner(jobId, owner)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + jobId));
        return toJobResponse(job);
    }

    @Transactional(readOnly = true)
    public AnalysisResultResponse getJobResults(Long jobId, Long userId) {
        User owner = findUser(userId);
        AnalysisJob job = jobRepository.findByIdAndOwnerWithResult(jobId, owner)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + jobId));

        if (job.getStatus() != JobStatus.COMPLETED) {
            throw new IllegalStateException("Job is not completed yet");
        }

        AnalysisResult result = resultRepository.findByAnalysisJobWithAntiPatterns(job)
                .orElseThrow(() -> new ResourceNotFoundException("Results not found for job: " + jobId));

        AnalysisDiffResponse diff = buildDiffIfAvailable(result, job);

        return toResultResponse(result, diff);
    }

    /**
     * Get the diff between the current job's result and the previous analysis
     * for the same project. Returns null if this is the first analysis.
     */
    @Transactional(readOnly = true)
    public AnalysisDiffResponse getDiff(Long jobId, Long userId) {
        User owner = findUser(userId);
        AnalysisJob job = jobRepository.findByIdAndOwnerWithResult(jobId, owner)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + jobId));

        if (job.getStatus() != JobStatus.COMPLETED) {
            throw new IllegalStateException("Job is not completed yet");
        }

        AnalysisResult result = resultRepository.findByAnalysisJobWithAntiPatterns(job)
                .orElseThrow(() -> new ResourceNotFoundException("Results not found for job: " + jobId));

        AnalysisDiffResponse diff = buildDiffIfAvailable(result, job);
        if (diff == null) {
            throw new ResourceNotFoundException("No previous analysis found for comparison");
        }

        return diff;
    }

    /**
     * Get the full analysis history for a project — all completed analysis
     * results ordered newest first. Each result includes a diff against
     * its predecessor (if any).
     */
    @Transactional(readOnly = true)
    public List<AnalysisResultResponse> getProjectHistory(Long projectId, Long userId) {
        Project projectEntity = findProjectForUser(projectId, userId);
        List<AnalysisResult> results = resultRepository.findAllByProjectWithAntiPatterns(projectEntity);

        List<AnalysisResultResponse> responses = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            AnalysisResult current = results.get(i);
            AnalysisDiffResponse diff = null;
            if (i + 1 < results.size()) {
                AnalysisResult previous = results.get(i + 1);
                int analysisNum = current.getAnalysisJob().getAnalysisNumber() != null
                        ? current.getAnalysisJob().getAnalysisNumber() : (results.size() - i);
                diff = analysisDiffService.buildDiff(current, previous, analysisNum);
            }
            responses.add(toResultResponse(current, diff));
        }
        return responses;
    }

    @Transactional(readOnly = true)
    public List<AnalysisJobResponse> getRecentJobsForUser(Long userId) {
        User owner = findUser(userId);
        return jobRepository.findByOwner(owner,
                        PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent().stream()
                .map(this::toJobResponse)
                .toList();
    }

    @Transactional
    public void cancelJob(Long jobId, Long userId) {
        User owner = findUser(userId);
        AnalysisJob job = jobRepository.findByIdAndOwner(jobId, owner)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + jobId));

        if (job.getStatus() == JobStatus.COMPLETED || job.getStatus() == JobStatus.FAILED) {
            throw new IllegalStateException("Cannot cancel a finished job");
        }

        job.cancel();
        jobRepository.save(job);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
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
                job.getErrorMessage(),
                job.getAnalysisNumber()
        );
    }

    private AnalysisResultResponse toResultResponse(AnalysisResult result, AnalysisDiffResponse diff) {
        List<AntiPatternResponse> antiPatterns = result.getDetectedAntiPatterns().stream()
                .map(this::toAntiPatternResponse)
                .toList();

        DependencyGraphResponse graph = parseDependencyGraph(result.getDependencyGraphJson());

        HealthScoreBreakdownResponse breakdown = healthScoreCalculator.calculate(result);

        return new AnalysisResultResponse(
                result.getId(),
                result.getAnalysisJob().getId(),
                breakdown.overallScore(),
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
                graph,
                breakdown,
                diff
        );
    }

    private AnalysisDiffResponse buildDiffIfAvailable(AnalysisResult current, AnalysisJob job) {
        Project project = job.getProject();
        return resultRepository.findPreviousResultForProject(project, current.getId())
                .map(previous -> {
                    int analysisNum = job.getAnalysisNumber() != null ? job.getAnalysisNumber() : 1;
                    return analysisDiffService.buildDiff(current, previous, analysisNum);
                })
                .orElse(null);
    }

    private Project findProjectForUser(Long projectId, Long userId) {
        User owner = findUser(userId);
        return projectRepository.findByIdAndOwner(projectId, owner)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
    }

    private AntiPatternResponse toAntiPatternResponse(DetectedAntiPattern pattern) {
        List<String> affectedServices = parseJsonList(pattern.getAffectedServicesJson());
        List<AntiPatternResponse.CodeSnippet> codeSnippets = parseCodeSnippets(pattern.getCodeSnippetsJson());

        return new AntiPatternResponse(
                pattern.getId(),
                pattern.getPatternType(),
                pattern.getSeverity(),
                pattern.getDescription(),
                affectedServices,
                pattern.getRemediation(),
                codeSnippets
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

    private List<AntiPatternResponse.CodeSnippet> parseCodeSnippets(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(json, new TypeReference<>() {});
            return raw.stream().map(m -> new AntiPatternResponse.CodeSnippet(
                    (String) m.getOrDefault("file", "unknown"),
                    ((Number) m.getOrDefault("startLine", 0)).intValue(),
                    ((Number) m.getOrDefault("endLine", 0)).intValue(),
                    ((Number) m.getOrDefault("highlightLine", 0)).intValue(),
                    (String) m.getOrDefault("snippet", "")
            )).toList();
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
