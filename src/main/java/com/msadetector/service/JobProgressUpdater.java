package com.msadetector.service;

import com.msadetector.entity.AnalysisJob;
import com.msadetector.entity.AnalysisResult;
import com.msadetector.enums.JobStatus;
import com.msadetector.repository.AnalysisJobRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles analysis job progress updates in independent transactions so that
 * progress is immediately visible to polling clients.
 * <p>
 * Each update runs in {@code REQUIRES_NEW}, committing instantly regardless
 * of the outer analysis transaction.
 */
@Component
public class JobProgressUpdater {

    private final AnalysisJobRepository jobRepository;

    public JobProgressUpdater(AnalysisJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void startJob(Long jobId) {
        AnalysisJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) return;
        job.start();
        jobRepository.saveAndFlush(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateProgress(Long jobId, JobStatus status, String phase, String currentService, int completed, int total) {
        AnalysisJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) return;
        job.updateProgress(status, phase, currentService, completed, total);
        jobRepository.saveAndFlush(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeJob(Long jobId, AnalysisResult result) {
        AnalysisJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) return;
        job.complete(result);
        jobRepository.saveAndFlush(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failJob(Long jobId, String errorMessage) {
        AnalysisJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) return;
        job.fail(errorMessage);
        jobRepository.saveAndFlush(job);
    }
}

