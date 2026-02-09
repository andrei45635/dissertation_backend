package com.msadetector.controller;

import com.msadetector.dto.AnalysisJobResponse;
import com.msadetector.dto.AnalysisResultResponse;
import com.msadetector.service.JobService;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<AnalysisJobResponse> getJobStatus(@PathVariable Long id) {
        return ResponseEntity.ok(jobService.getJobStatus(id));
    }

    @GetMapping("/{id}/results")
    public ResponseEntity<AnalysisResultResponse> getJobResults(@PathVariable Long id) {
        return ResponseEntity.ok(jobService.getJobResults(id));
    }

    @GetMapping("/recent")
    public ResponseEntity<List<AnalysisJobResponse>> getRecentJobs() {
        return ResponseEntity.ok(jobService.getRecentJobs());
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelJob(@PathVariable Long id) {
        jobService.cancelJob(id);
        return ResponseEntity.ok().build();
    }
}
