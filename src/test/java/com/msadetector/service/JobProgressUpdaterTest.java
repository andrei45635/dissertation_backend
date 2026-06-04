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

        boolean started = updater.startJob(99L);

        assertFalse(started);
        verify(jobRepository, never()).saveAndFlush(any());
    }

    @Test
    void startJob_cancelledJob_doesNotOverwriteStatus() {
        job.setStatus(JobStatus.CANCELLED);
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

        boolean started = updater.startJob(1L);

        assertFalse(started);
        assertEquals(JobStatus.CANCELLED, job.getStatus());
        verify(jobRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateProgress_updatesAllFields() {
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

        boolean updated = updater.updateProgress(1L, JobStatus.ANALYZING_SERVICES, "Analyzing", "user-service", 2, 5);

        assertTrue(updated);
        assertEquals(JobStatus.ANALYZING_SERVICES, job.getStatus());
        assertEquals("Analyzing", job.getCurrentPhase());
        assertEquals("user-service", job.getCurrentService());
        assertEquals(2, job.getServicesCompleted());
        assertEquals(5, job.getTotalServices());
        assertEquals(40, job.getProgressPercentage());
        verify(jobRepository).saveAndFlush(job);
    }

    @Test
    void updateProgress_cancelledJob_doesNotOverwriteStatus() {
        job.setStatus(JobStatus.CANCELLED);
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

        boolean updated = updater.updateProgress(1L, JobStatus.ANALYZING_SERVICES, "Analyzing", "svc", 1, 2);

        assertFalse(updated);
        assertEquals(JobStatus.CANCELLED, job.getStatus());
        assertNull(job.getCurrentPhase());
        verify(jobRepository, never()).saveAndFlush(any());
    }

    @Test
    void completeJob_setsStatusToCompleted() {
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));
        AnalysisResult result = AnalysisResult.builder().build();

        boolean completed = updater.completeJob(1L, result);

        assertTrue(completed);
        assertEquals(JobStatus.COMPLETED, job.getStatus());
        assertNotNull(job.getCompletedAt());
        assertEquals(100, job.getProgressPercentage());
        verify(jobRepository).saveAndFlush(job);
    }

    @Test
    void completeJob_cancelledJob_doesNotOverwriteStatus() {
        job.setStatus(JobStatus.CANCELLED);
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));
        AnalysisResult result = AnalysisResult.builder().build();

        boolean completed = updater.completeJob(1L, result);

        assertFalse(completed);
        assertEquals(JobStatus.CANCELLED, job.getStatus());
        assertNull(job.getResult());
        verify(jobRepository, never()).saveAndFlush(any());
    }

    @Test
    void failJob_setsStatusToFailed() {
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

        boolean failed = updater.failJob(1L, "Out of memory");

        assertTrue(failed);
        assertEquals(JobStatus.FAILED, job.getStatus());
        assertEquals("Out of memory", job.getErrorMessage());
        assertNotNull(job.getCompletedAt());
        verify(jobRepository).saveAndFlush(job);
    }

    @Test
    void failJob_jobNotFound_doesNothing() {
        when(jobRepository.findById(42L)).thenReturn(Optional.empty());

        boolean failed = updater.failJob(42L, "Error");

        assertFalse(failed);
        verify(jobRepository, never()).saveAndFlush(any());
    }

    @Test
    void failJob_cancelledJob_doesNotOverwriteStatus() {
        job.setStatus(JobStatus.CANCELLED);
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

        boolean failed = updater.failJob(1L, "Error");

        assertFalse(failed);
        assertEquals(JobStatus.CANCELLED, job.getStatus());
        assertNull(job.getErrorMessage());
        verify(jobRepository, never()).saveAndFlush(any());
    }
}

