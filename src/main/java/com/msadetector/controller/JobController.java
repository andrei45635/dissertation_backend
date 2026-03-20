package com.msadetector.controller;

import com.msadetector.dto.AnalysisDiffResponse;
import com.msadetector.dto.AnalysisJobResponse;
import com.msadetector.dto.AnalysisResultResponse;
import com.msadetector.security.UserPrincipal;
import com.msadetector.service.JobService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<AnalysisJobResponse> getJobStatus(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(jobService.getJobStatus(id, principal.getId()));
    }

    @GetMapping("/{id}/results")
    public ResponseEntity<AnalysisResultResponse> getJobResults(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(jobService.getJobResults(id, principal.getId()));
    }

    @GetMapping("/{id}/diff")
    public ResponseEntity<AnalysisDiffResponse> getJobDiff(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(jobService.getDiff(id, principal.getId()));
    }

    @GetMapping("/recent")
    public ResponseEntity<List<AnalysisJobResponse>> getRecentJobs(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(jobService.getRecentJobsForUser(principal.getId()));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelJob(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        jobService.cancelJob(id, principal.getId());
        return ResponseEntity.ok().build();
    }
}
