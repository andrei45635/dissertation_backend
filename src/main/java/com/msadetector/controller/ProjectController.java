package com.msadetector.controller;

import com.msadetector.dto.AnalysisResultResponse;
import com.msadetector.dto.GitCloneRequest;
import com.msadetector.dto.ProjectResponse;
import com.msadetector.dto.ReanalyzeRequest;
import com.msadetector.dto.UploadResponse;
import com.msadetector.security.UserPrincipal;
import com.msadetector.service.JobService;
import com.msadetector.service.ProjectService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final JobService jobService;

    public ProjectController(ProjectService projectService, JobService jobService) {
        this.projectService = projectService;
        this.jobService = jobService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> uploadProject(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") @NotBlank String name,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UploadResponse response = projectService.uploadAndAnalyze(file, name, principal.getId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/clone")
    public ResponseEntity<UploadResponse> cloneProject(
            @Valid @RequestBody GitCloneRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UploadResponse response = projectService.cloneAndAnalyze(
                request.repoUrl(),
                request.name(),
                request.branch(),
                principal.getId()
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ProjectResponse>> getAllProjects(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(projectService.getProjectsForUser(principal.getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> getProject(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(projectService.getProjectForUser(id, principal.getId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        projectService.deleteProjectForUser(id, principal.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<AnalysisResultResponse>> getProjectHistory(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(jobService.getProjectHistory(id, principal.getId()));
    }

    @PostMapping("/{id}/reanalyze")
    public ResponseEntity<UploadResponse> reanalyzeProject(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ReanalyzeRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        String repoUrl = request != null ? request.repoUrl() : null;
        String branch = request != null ? request.branch() : null;
        UploadResponse response = projectService.reanalyze(id, principal.getId(), repoUrl, branch);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping(value = "/{id}/reupload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> reuploadProject(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UploadResponse response = projectService.reuploadAndAnalyze(id, file, principal.getId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
