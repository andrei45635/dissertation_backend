package com.msadetector.service;

import com.msadetector.dto.MicroserviceResponse;
import com.msadetector.dto.ProjectResponse;
import com.msadetector.dto.UploadResponse;
import com.msadetector.entity.AnalysisJob;
import com.msadetector.entity.Project;
import com.msadetector.enums.SourceType;
import com.msadetector.exception.InvalidFileException;
import com.msadetector.exception.ResourceNotFoundException;
import com.msadetector.repository.AnalysisJobRepository;
import com.msadetector.repository.ProjectRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final AnalysisJobRepository analysisJobRepository;
    private final AnalysisWorker analysisWorker;
    private final Path workspaceDir;

    public ProjectService(
            ProjectRepository projectRepository,
            AnalysisJobRepository analysisJobRepository,
            AnalysisWorker analysisWorker,
            @Value("${app.analysis.workspace-dir}") String workspaceDir
    ) {
        this.projectRepository = projectRepository;
        this.analysisJobRepository = analysisJobRepository;
        this.analysisWorker = analysisWorker;
        this.workspaceDir = Path.of(workspaceDir);
    }

    @Transactional
    public UploadResponse uploadAndAnalyze(MultipartFile file, String projectName) {
        validateFile(file);

        Project project = Project.builder()
                .name(projectName)
                .sourceType(SourceType.UPLOAD)
                .build();
        project = projectRepository.save(project);

        Path projectDir = extractZip(file, project.getId());
        project.setLocalPath(projectDir.toString());
        projectRepository.saveAndFlush(project);

        AnalysisJob job = AnalysisJob.builder()
                .project(project)
                .build();
        job = analysisJobRepository.saveAndFlush(job);

        Long jobId = job.getId();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                analysisWorker.processJob(jobId);
            }
        });

        return new UploadResponse(project.getId(), job.getId());
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidFileException("File is empty");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".zip")) {
            throw new InvalidFileException("File must be a ZIP archive");
        }

        long maxSize = 500 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new InvalidFileException("File size exceeds 500MB limit");
        }
    }

    private Path extractZip(MultipartFile file, Long projectId) {
        Path projectDir = workspaceDir.resolve(projectId.toString());

        try {
            Files.createDirectories(projectDir);

            try (InputStream is = file.getInputStream();
                 ZipInputStream zis = new ZipInputStream(is)) {

                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    Path entryPath = projectDir.resolve(entry.getName()).normalize();

                    if (!entryPath.startsWith(projectDir)) {
                        throw new InvalidFileException("Invalid zip entry: " + entry.getName());
                    }

                    if (entry.isDirectory()) {
                        Files.createDirectories(entryPath);
                    } else {
                        Files.createDirectories(entryPath.getParent());
                        Files.copy(zis, entryPath);
                    }

                    zis.closeEntry();
                }
            }

            return normalizeProjectRoot(projectDir);

        } catch (IOException e) {
            throw new InvalidFileException("Failed to extract ZIP file: " + e.getMessage());
        }
    }

    private Path normalizeProjectRoot(Path projectDir) {
        try {
            List<Path> topLevel = Files.list(projectDir).toList();

            if (topLevel.size() == 1 && Files.isDirectory(topLevel.getFirst())) {
                return topLevel.getFirst();
            }

            return projectDir;
        } catch (IOException e) {
            return projectDir;
        }
    }

    public List<ProjectResponse> getAllProjects() {
        return projectRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public ProjectResponse getProject(Long id) {
        Project project = projectRepository.findByIdWithMicroservices(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
        return toResponse(project);
    }

    @Transactional
    public void deleteProject(Long id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));

        if (project.getLocalPath() != null) {
            try {
                deleteDirectory(Path.of(project.getLocalPath()));
            } catch (IOException e) {
                // log warning but continue with deletion
            }
        }

        projectRepository.delete(project);
    }

    private void deleteDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // ignore
                        }
                    });
        }
    }

    private ProjectResponse toResponse(Project project) {
        List<MicroserviceResponse> microservices = project.getMicroservices().stream()
                .map(ms -> new MicroserviceResponse(
                        ms.getId(),
                        ms.getName(),
                        ms.getRelativePath(),
                        ms.getLinesOfCode(),
                        ms.getNumberOfEndpoints()
                ))
                .toList();

        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getSourceType(),
                project.getSourceUrl(),
                project.getCreatedAt(),
                microservices
        );
    }
}
