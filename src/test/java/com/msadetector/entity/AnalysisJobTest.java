package com.msadetector.entity;

import com.msadetector.enums.JobStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnalysisJobTest {

    @Test
    void start_setsStatusAndTimestamp() {
        AnalysisJob job = AnalysisJob.builder().build();

        job.start();

        assertEquals(JobStatus.CLONING, job.getStatus());
        assertNotNull(job.getStartedAt());
    }

    @Test
    void complete_setsStatusAndResult() {
        AnalysisJob job = AnalysisJob.builder().build();
        AnalysisResult result = AnalysisResult.builder().build();

        job.complete(result);

        assertEquals(JobStatus.COMPLETED, job.getStatus());
        assertEquals(100, job.getProgressPercentage());
        assertNotNull(job.getCompletedAt());
        assertEquals(result, job.getResult());
        assertEquals(job, result.getAnalysisJob());
    }

    @Test
    void fail_setsStatusAndErrorMessage() {
        AnalysisJob job = AnalysisJob.builder().build();

        job.fail("Something broke");

        assertEquals(JobStatus.FAILED, job.getStatus());
        assertEquals("Something broke", job.getErrorMessage());
        assertNotNull(job.getCompletedAt());
    }

    @Test
    void cancel_setsStatusToCancelled() {
        AnalysisJob job = AnalysisJob.builder().build();

        job.cancel();

        assertEquals(JobStatus.CANCELLED, job.getStatus());
        assertNotNull(job.getCompletedAt());
    }

    @Test
    void updateProgress_calculatesPercentage() {
        AnalysisJob job = AnalysisJob.builder().build();

        job.updateProgress(JobStatus.ANALYZING_SERVICES, "Analyzing", "order-service", 3, 5);

        assertEquals(JobStatus.ANALYZING_SERVICES, job.getStatus());
        assertEquals("Analyzing", job.getCurrentPhase());
        assertEquals("order-service", job.getCurrentService());
        assertEquals(3, job.getServicesCompleted());
        assertEquals(5, job.getTotalServices());
        assertEquals(60, job.getProgressPercentage());
    }

    @Test
    void updateProgress_zeroTotal_percentageIsZero() {
        AnalysisJob job = AnalysisJob.builder().build();

        job.updateProgress(JobStatus.DETECTING_SERVICES, "Detecting", null, 0, 0);

        assertEquals(0, job.getProgressPercentage());
    }

    @Test
    void defaultValues() {
        AnalysisJob job = AnalysisJob.builder().build();

        assertEquals(JobStatus.PENDING, job.getStatus());
        assertEquals(0, job.getServicesCompleted());
        assertEquals(0, job.getTotalServices());
        assertEquals(0, job.getProgressPercentage());
        assertEquals(1, job.getAnalysisNumber());
        assertTrue(job.isRunDesignite());
        assertTrue(job.isDetectCyclicDependencies());
        assertTrue(job.isDetectSharedDatabases());
        assertTrue(job.isDetectNanoServices());
        assertTrue(job.isDetectGodServices());
        assertTrue(job.isDetectChattyServices());
        assertTrue(job.isDetectHardcodedEndpoints());
    }
}

