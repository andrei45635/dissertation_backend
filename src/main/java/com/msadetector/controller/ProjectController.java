package com.msadetector.controller;

import com.msadetector.dto.GitCloneRequest;
import com.msadetector.dto.ProjectResponse;
import com.msadetector.dto.UploadResponse;
import com.msadetector.service.ProjectService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> uploadProject(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") @NotBlank String name
    ) {
        UploadResponse response = projectService.uploadAndAnalyze(file, name);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/clone")
    public ResponseEntity<UploadResponse> cloneProject(@Valid @RequestBody GitCloneRequest request) {
        UploadResponse response = projectService.cloneAndAnalyze(
                request.repoUrl(),
                request.name(),
                request.branch()
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ProjectResponse>> getAllProjects() {
        return ResponseEntity.ok(projectService.getAllProjects());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> getProject(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.getProject(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable Long id) {
        projectService.deleteProject(id);
        return ResponseEntity.noContent().build();
    }
}
