package com.msadetector.service;

import com.msadetector.dto.AnalysisOptionsRequest;
import com.msadetector.dto.MicroserviceResponse;
import com.msadetector.dto.ProjectResponse;
import com.msadetector.dto.UploadResponse;
import com.msadetector.entity.AnalysisJob;
import com.msadetector.entity.Project;
import com.msadetector.entity.User;
import com.msadetector.enums.JobStatus;
import com.msadetector.enums.SourceType;
import com.msadetector.exception.AnalysisInProgressException;
import com.msadetector.exception.InvalidFileException;
import com.msadetector.exception.ResourceNotFoundException;
import com.msadetector.repository.AnalysisJobRepository;
import com.msadetector.repository.MicroserviceRepository;
import com.msadetector.repository.ProjectRepository;
import com.msadetector.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class ProjectService {

    private static final int ZIP_BUFFER_SIZE = 8192;
    private static final Set<JobStatus> ACTIVE_JOB_STATUSES = EnumSet.of(
            JobStatus.PENDING,
            JobStatus.CLONING,
            JobStatus.DETECTING_SERVICES,
            JobStatus.ANALYZING_SERVICES,
            JobStatus.BUILDING_GRAPH,
            JobStatus.DETECTING_PATTERNS
    );

    private final ProjectRepository projectRepository;
    private final AnalysisJobRepository analysisJobRepository;
    private final MicroserviceRepository microserviceRepository;
    private final UserRepository userRepository;
    private final AnalysisWorker analysisWorker;
    private final GitCloneService gitCloneService;
    private final Path workspaceDir;
    private final long maxZipUncompressedBytes;
    private final int maxZipEntries;
    private final int maxZipDepth;

    public ProjectService(
            ProjectRepository projectRepository,
            AnalysisJobRepository analysisJobRepository,
            MicroserviceRepository microserviceRepository,
            UserRepository userRepository,
            AnalysisWorker analysisWorker,
            GitCloneService gitCloneService,
            @Value("${app.analysis.workspace-dir}") String workspaceDir,
            @Value("${app.upload.max-uncompressed-size-bytes:1073741824}") long maxZipUncompressedBytes,
            @Value("${app.upload.max-entries:100000}") int maxZipEntries,
            @Value("${app.upload.max-depth:50}") int maxZipDepth
    ) {
        this.projectRepository = projectRepository;
        this.analysisJobRepository = analysisJobRepository;
        this.microserviceRepository = microserviceRepository;
        this.userRepository = userRepository;
        this.analysisWorker = analysisWorker;
        this.gitCloneService = gitCloneService;
        this.workspaceDir = Path.of(workspaceDir);
        this.maxZipUncompressedBytes = requirePositive(maxZipUncompressedBytes, "max uncompressed ZIP size");
        this.maxZipEntries = requirePositive(maxZipEntries, "max ZIP entries");
        this.maxZipDepth = requirePositive(maxZipDepth, "max ZIP depth");
    }

    @Transactional
    public UploadResponse uploadAndAnalyze(MultipartFile file, String projectName,
                                           AnalysisOptionsRequest options, Long userId) {
        validateFile(file);

        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        Project project = Project.builder()
                .name(projectName)
                .sourceType(SourceType.UPLOAD)
                .build();
        project.setOwner(owner);
        project = projectRepository.save(project);

        Path projectDir = extractZip(file, project.getId());
        project.setLocalPath(projectDir.toString());
        projectRepository.saveAndFlush(project);

        AnalysisJob job = AnalysisJob.builder()
                .project(project)
                .build();
        applyAnalysisOptions(job, options);
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

    @Transactional
    public UploadResponse cloneAndAnalyze(String repoUrl, String projectName, String branch,
                                          AnalysisOptionsRequest options, Long userId) {
        gitCloneService.validateRepoUrl(repoUrl);

        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        String host = gitCloneService.extractHost(repoUrl);
        SourceType sourceType = host.contains("gitlab") ? SourceType.GITLAB : SourceType.GITHUB;

        if (projectName == null || projectName.isBlank()) {
            projectName = gitCloneService.extractRepoName(repoUrl);
        }

        String effectiveBranch = (branch != null && !branch.isBlank()) ? branch : null;

        Project project = Project.builder()
                .name(projectName)
                .sourceType(sourceType)
                .sourceUrl(repoUrl)
                .branch(effectiveBranch)
                .build();
        project.setOwner(owner);
        project = projectRepository.save(project);

        Path projectDir = gitCloneService.cloneRepository(repoUrl, project.getId(), effectiveBranch);
        project.setLocalPath(projectDir.toString());
        projectRepository.saveAndFlush(project);

        AnalysisJob job = AnalysisJob.builder()
                .project(project)
                .build();
        applyAnalysisOptions(job, options);
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

    @Transactional(readOnly = true)
    public List<ProjectResponse> getProjectsForUser(Long userId) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        return projectRepository.findByOwnerOrderByCreatedAtDesc(owner).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProjectForUser(Long projectId, Long userId) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        Project project = projectRepository.findByIdAndOwner(projectId, owner)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
        return toResponse(project);
    }

    @Transactional
    public void deleteProjectForUser(Long projectId, Long userId) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        Project project = projectRepository.findByIdAndOwner(projectId, owner)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));

        if (project.getLocalPath() != null) {
            try {
                deleteDirectory(Path.of(project.getLocalPath()));
            } catch (IOException e) {
            }
        }

        projectRepository.delete(project);
    }

    /**
     * Re-analyze an existing project using its current source.
     * <ul>
     *   <li>Git projects: re-clones the repo (picks up new commits).</li>
     *   <li>Upload projects: re-uses the existing local files.</li>
     * </ul>
     * Optionally, the caller can supply a {@code repoUrl} + {@code branch}
     * to <b>switch</b> the project's source from Upload → Git (or update
     * the Git URL/branch).
     */
    @Transactional
    public UploadResponse reanalyze(Long projectId, Long userId,
                                    String repoUrl, String branch,
                                    AnalysisOptionsRequest options) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        Project project = findProjectForUpdate(projectId, owner);
        ensureNoActiveAnalysis(project);

        if (repoUrl != null && !repoUrl.isBlank()) {
            gitCloneService.validateRepoUrl(repoUrl);

            String host = gitCloneService.extractHost(repoUrl);
            SourceType newSourceType = host.contains("gitlab") ? SourceType.GITLAB : SourceType.GITHUB;
            String effectiveBranch = (branch != null && !branch.isBlank()) ? branch : null;

            cleanLocalPath(project);

            Path projectDir = gitCloneService.cloneRepository(repoUrl, project.getId(), effectiveBranch);
            project.setSourceType(newSourceType);
            project.setSourceUrl(repoUrl);
            project.setBranch(effectiveBranch);
            project.setLocalPath(projectDir.toString());

        } else if (project.getSourceUrl() != null && !project.getSourceUrl().isBlank()) {
            cleanLocalPath(project);

            Path projectDir = gitCloneService.cloneRepository(
                    project.getSourceUrl(), project.getId(), project.getBranch());
            project.setLocalPath(projectDir.toString());
        }
        return createReanalysisJob(project, options);
    }

    /**
     * Re-upload a new ZIP file for an existing project and trigger
     * a fresh analysis.  The project's source type is set to UPLOAD
     * (regardless of what it was before).
     */
    @Transactional
    public UploadResponse reuploadAndAnalyze(Long projectId, MultipartFile file,
                                             AnalysisOptionsRequest options, Long userId) {
        validateFile(file);

        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        Project project = findProjectForUpdate(projectId, owner);
        ensureNoActiveAnalysis(project);

        cleanLocalPath(project);

        Path projectDir = extractZip(file, project.getId());
        project.setSourceType(SourceType.UPLOAD);
        project.setSourceUrl(null);
        project.setBranch(null);
        project.setLocalPath(projectDir.toString());

        return createReanalysisJob(project, options);
    }

    private Project findProjectForUpdate(Long projectId, User owner) {
        return projectRepository.findByIdAndOwnerForUpdate(projectId, owner)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
    }

    private void ensureNoActiveAnalysis(Project project) {
        if (analysisJobRepository.existsByProjectAndStatusIn(project, ACTIVE_JOB_STATUSES)) {
            throw new AnalysisInProgressException(
                    "Project already has an analysis in progress. Wait for it to finish before starting another one."
            );
        }
    }

    private UploadResponse createReanalysisJob(Project project, AnalysisOptionsRequest options) {
        projectRepository.saveAndFlush(project);

        int nextNumber = analysisJobRepository.countByProject(project) + 1;

        AnalysisJob job = AnalysisJob.builder()
                .project(project)
                .analysisNumber(nextNumber)
                .build();
        applyAnalysisOptions(job, options);
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

    private void applyAnalysisOptions(AnalysisJob job, AnalysisOptionsRequest options) {
        if (options == null) {
            return;
        }

        if (options.runDesignite() != null) job.setRunDesignite(options.runDesignite());
        if (options.detectCyclicDependencies() != null) job.setDetectCyclicDependencies(options.detectCyclicDependencies());
        if (options.detectSharedDatabases() != null) job.setDetectSharedDatabases(options.detectSharedDatabases());
        if (options.detectNanoServices() != null) job.setDetectNanoServices(options.detectNanoServices());
        if (options.detectGodServices() != null) job.setDetectGodServices(options.detectGodServices());
        if (options.detectChattyServices() != null) job.setDetectChattyServices(options.detectChattyServices());
        if (options.detectHardcodedEndpoints() != null) job.setDetectHardcodedEndpoints(options.detectHardcodedEndpoints());
        if (options.detectDistributedMonoliths() != null) job.setDetectDistributedMonoliths(options.detectDistributedMonoliths());
        if (options.detectApiVersioningAbsence() != null) job.setDetectApiVersioningAbsence(options.detectApiVersioningAbsence());
        if (options.detectWrongCuts() != null) job.setDetectWrongCuts(options.detectWrongCuts());
        if (options.detectEsbMisuse() != null) job.setDetectEsbMisuse(options.detectEsbMisuse());

        if (options.nanoServiceMaxLoc() != null) job.setNanoServiceMaxLoc(options.nanoServiceMaxLoc());
        if (options.nanoServiceMaxEndpoints() != null) job.setNanoServiceMaxEndpoints(options.nanoServiceMaxEndpoints());
        if (options.chattyServiceMinCalls() != null) job.setChattyServiceMinCalls(options.chattyServiceMinCalls());
        if (options.godServiceFieldCount() != null) job.setGodServiceFieldCount(options.godServiceFieldCount());
        if (options.godServicePublicMethods() != null) job.setGodServicePublicMethods(options.godServicePublicMethods());
        if (options.godServiceLoc() != null) job.setGodServiceLoc(options.godServiceLoc());
        if (options.godServiceImportDomains() != null) job.setGodServiceImportDomains(options.godServiceImportDomains());
        if (options.godServiceConstructorParams() != null) job.setGodServiceConstructorParams(options.godServiceConstructorParams());
        if (options.godServiceTccThreshold() != null) job.setGodServiceTccThreshold(options.godServiceTccThreshold());
        if (options.godServiceMinMetrics() != null) job.setGodServiceMinMetrics(options.godServiceMinMetrics());
        if (options.esbMediatorThreshold() != null) job.setEsbMediatorThreshold(options.esbMediatorThreshold());
        if (options.distributedMonolithHighCoupling() != null) {
            job.setDistributedMonolithHighCoupling(options.distributedMonolithHighCoupling());
        }
        if (options.distributedMonolithConnectedRatio() != null) {
            job.setDistributedMonolithConnectedRatio(options.distributedMonolithConnectedRatio());
        }
        if (options.distributedMonolithModerateCoupling() != null) {
            job.setDistributedMonolithModerateCoupling(options.distributedMonolithModerateCoupling());
        }
    }

    private void cleanLocalPath(Project project) {
        if (project.getLocalPath() != null) {
            try {
                deleteDirectory(Path.of(project.getLocalPath()));
            } catch (IOException e) {
            }
        }
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
        Path projectDir = workspaceDir.resolve(projectId.toString()).normalize();

        try {
            Files.createDirectories(projectDir);

            try (InputStream is = file.getInputStream();
                 ZipInputStream zis = new ZipInputStream(is)) {

                ZipEntry entry;
                int entryCount = 0;
                long totalUncompressedBytes = 0;
                byte[] buffer = new byte[ZIP_BUFFER_SIZE];

                while ((entry = zis.getNextEntry()) != null) {
                    entryCount++;
                    if (entryCount > maxZipEntries) {
                        throw new InvalidFileException("ZIP archive contains too many entries");
                    }

                    Path entryPath = resolveZipEntry(projectDir, entry);
                    if (entryDepth(projectDir, entryPath) > maxZipDepth) {
                        throw new InvalidFileException("ZIP entry is too deeply nested: " + entry.getName());
                    }

                    if (entry.isDirectory()) {
                        Files.createDirectories(entryPath);
                    } else {
                        if (Files.exists(entryPath)) {
                            throw new InvalidFileException("Duplicate ZIP entry: " + entry.getName());
                        }
                        Files.createDirectories(entryPath.getParent());
                        try (OutputStream os = Files.newOutputStream(
                                entryPath,
                                StandardOpenOption.CREATE_NEW,
                                StandardOpenOption.WRITE)) {
                            int bytesRead;
                            while ((bytesRead = zis.read(buffer)) != -1) {
                                totalUncompressedBytes += bytesRead;
                                if (totalUncompressedBytes > maxZipUncompressedBytes) {
                                    throw new InvalidFileException("ZIP archive exceeds decompressed size limit");
                                }
                                os.write(buffer, 0, bytesRead);
                            }
                        }
                    }

                    zis.closeEntry();
                }
            }

            return normalizeProjectRoot(projectDir);

        } catch (InvalidFileException e) {
            deleteDirectoryQuietly(projectDir);
            throw e;
        } catch (FileAlreadyExistsException e) {
            deleteDirectoryQuietly(projectDir);
            throw new InvalidFileException("Duplicate ZIP entry: " + e.getFile());
        } catch (IOException e) {
            deleteDirectoryQuietly(projectDir);
            throw new InvalidFileException("Failed to extract ZIP file: " + e.getMessage());
        }
    }

    private Path resolveZipEntry(Path projectDir, ZipEntry entry) {
        String entryName = entry.getName();
        if (entryName == null || entryName.isBlank()) {
            throw new InvalidFileException("Invalid empty ZIP entry");
        }

        Path entryPath = projectDir.resolve(entryName).normalize();
        if (!entryPath.startsWith(projectDir)) {
            throw new InvalidFileException("Invalid ZIP entry: " + entryName);
        }

        return entryPath;
    }

    private int entryDepth(Path projectDir, Path entryPath) {
        return entryPath.getNameCount() - projectDir.getNameCount();
    }

    private static long requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
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

    private void deleteDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                        }
                    });
        }
    }

    private void deleteDirectoryQuietly(Path dir) {
        try {
            deleteDirectory(dir);
        } catch (IOException e) {
        }
    }

    private ProjectResponse toResponse(Project project) {
        AnalysisJob latestJob = analysisJobRepository.findFirstByProjectOrderByCreatedAtDesc(project)
                .orElse(null);
        Long latestJobId = latestJob != null ? latestJob.getId() : null;

        List<com.msadetector.entity.Microservice> latestMicroservices = latestJob != null
                ? microserviceRepository.findByAnalysisJobOrderByNameAsc(latestJob)
                : List.of();

        List<MicroserviceResponse> microservices = latestMicroservices.stream()
                .map(ms -> new MicroserviceResponse(
                        ms.getId(),
                        ms.getName(),
                        ms.getRelativePath(),
                        ms.getLinesOfCode(),
                        ms.getNumberOfEndpoints()
                ))
                .toList();

        int analysisCount = analysisJobRepository.countByProject(project);

        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getSourceType(),
                project.getSourceUrl(),
                project.getBranch(),
                project.getCreatedAt(),
                microservices,
                analysisCount,
                latestJobId
        );
    }
}
