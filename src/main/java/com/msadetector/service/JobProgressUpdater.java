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
    public boolean startJob(Long jobId) {
        AnalysisJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null || job.getStatus() == JobStatus.CANCELLED) return false;
        job.start();
        jobRepository.saveAndFlush(job);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean updateProgress(Long jobId, JobStatus status, String phase, String currentService, int completed, int total) {
        AnalysisJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null || job.getStatus() == JobStatus.CANCELLED) return false;
        job.updateProgress(status, phase, currentService, completed, total);
        jobRepository.saveAndFlush(job);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean completeJob(Long jobId, AnalysisResult result) {
        AnalysisJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null || job.getStatus() == JobStatus.CANCELLED) return false;
        job.complete(result);
        jobRepository.saveAndFlush(job);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean failJob(Long jobId, String errorMessage) {
        AnalysisJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null || job.getStatus() == JobStatus.CANCELLED) return false;
        job.fail(errorMessage);
        jobRepository.saveAndFlush(job);
        return true;
    }
}

