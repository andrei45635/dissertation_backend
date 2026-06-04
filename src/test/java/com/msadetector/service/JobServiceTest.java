package com.msadetector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msadetector.dto.*;
import com.msadetector.entity.*;
import com.msadetector.enums.AntiPatternType;
import com.msadetector.enums.JobStatus;
import com.msadetector.enums.Severity;
import com.msadetector.exception.ResourceNotFoundException;
import com.msadetector.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock private AnalysisJobRepository jobRepository;
    @Mock private AnalysisResultRepository resultRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private HealthScoreCalculator healthScoreCalculator;
    @Mock private AnalysisDiffService analysisDiffService;

    @InjectMocks private JobService jobService;

    private User user;
    private Project project;
    private AnalysisJob job;
    private AnalysisResult result;

    @BeforeEach
    void setUp() {
        user = User.builder().name("Test").email("test@test.com").password("pass").build();
        user.setId(1L);

        project = Project.builder().name("test-project").build();
        project.setId(10L);
        project.setOwner(user);

        job = AnalysisJob.builder().project(project).status(JobStatus.COMPLETED).analysisNumber(1).build();
        job.setId(100L);
        job.setStartedAt(LocalDateTime.now());
        job.setCompletedAt(LocalDateTime.now());

        result = AnalysisResult.builder()
                .analysisJob(job)
                .healthScore(85)
                .servicesAnalyzed(3)
                .totalAntiPatterns(1)
                .totalCodeSmells(5)
                .totalLinesOfCode(10000)
                .averageServiceSize(3333.0)
                .cycleCount(0)
                .couplingCoefficient(0.1)
                .detectedAntiPatterns(new ArrayList<>())
                .build();
        result.setId(200L);
        result.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void getJobStatus_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jobRepository.findByIdAndOwner(100L, user)).thenReturn(Optional.of(job));

        AnalysisJobResponse response = jobService.getJobStatus(100L, 1L);

        assertEquals(100L, response.id());
        assertEquals(JobStatus.COMPLETED, response.status());
    }

    @Test
    void getJobStatus_notFound_throws() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jobRepository.findByIdAndOwner(999L, user)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> jobService.getJobStatus(999L, 1L));
    }

    @Test
    void getJobResults_jobNotCompleted_throws() {
        job.setStatus(JobStatus.PENDING);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jobRepository.findByIdAndOwnerWithResult(100L, user)).thenReturn(Optional.of(job));

        assertThrows(IllegalStateException.class, () -> jobService.getJobResults(100L, 1L));
    }

    @Test
    void getJobResults_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jobRepository.findByIdAndOwnerWithResult(100L, user)).thenReturn(Optional.of(job));
        when(resultRepository.findByAnalysisJobWithAntiPatterns(job)).thenReturn(Optional.of(result));
        when(resultRepository.findPreviousResultsByAnalysisNumber(
                eq(project), eq(job.getAnalysisNumber()), any(Pageable.class))).thenReturn(List.of());
        mockHealthScore();

        AnalysisResultResponse response = jobService.getJobResults(100L, 1L);

        assertNotNull(response);
        assertEquals(200L, response.id());
    }

    @Test
    void cancelJob_completed_throws() {
        job.setStatus(JobStatus.COMPLETED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jobRepository.findByIdAndOwner(100L, user)).thenReturn(Optional.of(job));

        assertThrows(IllegalStateException.class, () -> jobService.cancelJob(100L, 1L));
    }

    @Test
    void cancelJob_pending_success() {
        job.setStatus(JobStatus.PENDING);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jobRepository.findByIdAndOwner(100L, user)).thenReturn(Optional.of(job));

        jobService.cancelJob(100L, 1L);

        assertEquals(JobStatus.CANCELLED, job.getStatus());
        verify(jobRepository).save(job);
    }

    @Test
    void getRecentJobsForUser_returnsList() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jobRepository.findByOwner(eq(user), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(job)));

        List<AnalysisJobResponse> jobs = jobService.getRecentJobsForUser(1L);

        assertEquals(1, jobs.size());
    }

    @Test
    void getDiff_noPreviousAnalysis_throws() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jobRepository.findByIdAndOwnerWithResult(100L, user)).thenReturn(Optional.of(job));
        when(resultRepository.findByAnalysisJobWithAntiPatterns(job)).thenReturn(Optional.of(result));
        when(resultRepository.findPreviousResultsByAnalysisNumber(
                eq(project), eq(job.getAnalysisNumber()), any(Pageable.class))).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class, () -> jobService.getDiff(100L, 1L));
    }

    @Test
    void getProjectHistory_returnsOrderedResults() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(projectRepository.findByIdAndOwner(10L, user)).thenReturn(Optional.of(project));
        when(resultRepository.findAllByProjectWithAntiPatterns(project)).thenReturn(List.of(result));
        mockHealthScore();

        List<AnalysisResultResponse> history = jobService.getProjectHistory(10L, 1L);

        assertEquals(1, history.size());
    }

    private void mockHealthScore() {
        when(healthScoreCalculator.calculate(any()))
                .thenReturn(new HealthScoreBreakdownResponse(85, "B", List.of()));
    }
}
