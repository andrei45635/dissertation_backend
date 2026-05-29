package com.msadetector.service;

import com.msadetector.entity.AnalysisJob;
import com.msadetector.entity.AnalysisResult;
import com.msadetector.enums.JobStatus;
import com.msadetector.repository.AnalysisJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobProgressUpdaterTest {

    @Mock private AnalysisJobRepository jobRepository;
    @InjectMocks private JobProgressUpdater updater;

    private AnalysisJob job;

    @BeforeEach
    void setUp() {
        job = AnalysisJob.builder().build();
        job.setId(1L);
    }

    @Test
    void startJob_setsStatusToCloning() {
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

        updater.startJob(1L);

        assertEquals(JobStatus.CLONING, job.getStatus());
        assertNotNull(job.getStartedAt());
        verify(jobRepository).saveAndFlush(job);
    }

    @Test
    void startJob_jobNotFound_doesNothing() {
        when(jobRepository.findById(99L)).thenReturn(Optional.empty());

        updater.startJob(99L);

        verify(jobRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateProgress_updatesAllFields() {
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

        updater.updateProgress(1L, JobStatus.ANALYZING_SERVICES, "Analyzing", "user-service", 2, 5);

        assertEquals(JobStatus.ANALYZING_SERVICES, job.getStatus());
        assertEquals("Analyzing", job.getCurrentPhase());
        assertEquals("user-service", job.getCurrentService());
        assertEquals(2, job.getServicesCompleted());
        assertEquals(5, job.getTotalServices());
        assertEquals(40, job.getProgressPercentage());
        verify(jobRepository).saveAndFlush(job);
    }

    @Test
    void completeJob_setsStatusToCompleted() {
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));
        AnalysisResult result = AnalysisResult.builder().build();

        updater.completeJob(1L, result);

        assertEquals(JobStatus.COMPLETED, job.getStatus());
        assertNotNull(job.getCompletedAt());
        assertEquals(100, job.getProgressPercentage());
        verify(jobRepository).saveAndFlush(job);
    }

    @Test
    void failJob_setsStatusToFailed() {
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

        updater.failJob(1L, "Out of memory");

        assertEquals(JobStatus.FAILED, job.getStatus());
        assertEquals("Out of memory", job.getErrorMessage());
        assertNotNull(job.getCompletedAt());
        verify(jobRepository).saveAndFlush(job);
    }

    @Test
    void failJob_jobNotFound_doesNothing() {
        when(jobRepository.findById(42L)).thenReturn(Optional.empty());

        updater.failJob(42L, "Error");

        verify(jobRepository, never()).saveAndFlush(any());
    }
}

