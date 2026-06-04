package com.msadetector.service;

import com.msadetector.entity.AnalysisJob;
import com.msadetector.entity.AnalysisResult;
import com.msadetector.entity.Microservice;
import com.msadetector.entity.Project;
import com.msadetector.enums.JobStatus;
import com.msadetector.repository.AnalysisJobRepository;
import com.msadetector.repository.MicroserviceRepository;
import com.msadetector.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Service
public class AnalysisWorker {

    private static final Logger log = LoggerFactory.getLogger(AnalysisWorker.class);

    private final AnalysisJobRepository jobRepository;
    private final ProjectRepository projectRepository;
    private final MicroserviceRepository microserviceRepository;
    private final DesigniteService designiteService;
    private final MicroserviceDetector microserviceDetector;
    private final AntiPatternDetectorService antiPatternDetector;
    private final JobProgressUpdater progressUpdater;

    public AnalysisWorker(
            AnalysisJobRepository jobRepository,
            ProjectRepository projectRepository,
            MicroserviceRepository microserviceRepository,
            DesigniteService designiteService,
            MicroserviceDetector microserviceDetector,
            AntiPatternDetectorService antiPatternDetector,
            JobProgressUpdater progressUpdater
    ) {
        this.jobRepository = jobRepository;
        this.projectRepository = projectRepository;
        this.microserviceRepository = microserviceRepository;
        this.designiteService = designiteService;
        this.microserviceDetector = microserviceDetector;
        this.antiPatternDetector = antiPatternDetector;
        this.progressUpdater = progressUpdater;
    }

    @Async
    public void processJob(Long jobId) {
        AnalysisJob job = jobRepository.findByIdWithProject(jobId).orElse(null);
        if (job == null) {
            log.error("Job not found: {}", jobId);
            return;
        }

        try {
            if (!progressUpdater.startJob(jobId)) {
                log.info("Analysis job {} was cancelled before start", jobId);
                return;
            }

            Project project = job.getProject();
            Path projectPath = Path.of(project.getLocalPath());

            progressUpdater.updateProgress(jobId, JobStatus.DETECTING_SERVICES, "Scanning for microservices", null, 0, 0);
            if (isCancelled(jobId)) return;
            List<Microservice> microservices = detectMicroservices(job, project, projectPath);

            int total = microservices.size();
            int completed = 0;

            progressUpdater.updateProgress(jobId, JobStatus.ANALYZING_SERVICES, "Running code analysis", null, completed, total);
            if (isCancelled(jobId)) return;

            for (Microservice ms : microservices) {
                if (isCancelled(jobId)) return;
                progressUpdater.updateProgress(jobId, JobStatus.ANALYZING_SERVICES, "Analyzing service", ms.getName(), completed, total);
                if (isCancelled(jobId)) return;

                if (job.isRunDesignite()) {
                    Path servicePath = projectPath.resolve(ms.getRelativePath());
                    designiteService.analyzeService(ms, servicePath);
                }

                if (isCancelled(jobId)) return;
                completed++;
                progressUpdater.updateProgress(jobId, JobStatus.ANALYZING_SERVICES, "Analyzing service", ms.getName(), completed, total);
            }

            progressUpdater.updateProgress(jobId, JobStatus.BUILDING_GRAPH, "Building dependency graph", null, completed, total);
            if (isCancelled(jobId)) return;
            antiPatternDetector.buildDependencyGraph(project, job);

            progressUpdater.updateProgress(jobId, JobStatus.DETECTING_PATTERNS, "Detecting anti-patterns", null, completed, total);
            if (isCancelled(jobId)) return;
            AnalysisResult result = antiPatternDetector.detectAntiPatterns(project, job);

            if (isCancelled(jobId)) return;
            progressUpdater.completeJob(jobId, result);

            log.info("Analysis completed for job {}", jobId);

        } catch (Exception e) {
            log.error("Analysis failed for job {}", jobId, e);
            progressUpdater.failJob(jobId, e.getMessage());
        }
    }

    private boolean isCancelled(Long jobId) {
        boolean cancelled = jobRepository.findById(jobId)
                .map(job -> job.getStatus() == JobStatus.CANCELLED)
                .orElse(true);
        if (cancelled) {
            log.info("Analysis job {} was cancelled; stopping worker", jobId);
        }
        return cancelled;
    }

    private List<Microservice> detectMicroservices(AnalysisJob job, Project project, Path projectPath) {
        List<MicroserviceDetector.DetectedService> detectedServices =
                microserviceDetector.detectServicesWithConfidence(projectPath);
        List<Microservice> microservices = new ArrayList<>();

        for (MicroserviceDetector.DetectedService detected : detectedServices) {
            Path servicePath = detected.path();
            String name = servicePath.getFileName().toString();
            String relativePath = projectPath.relativize(servicePath).toString();

            Microservice ms = Microservice.builder()
                    .name(name)
                    .relativePath(relativePath)
                    .project(project)
                    .analysisJob(job)
                    .linesOfCode(countLinesOfCode(servicePath))
                    .detectionConfidence(detected.confidence().name())
                    .detectionSignal(detected.signal())
                    .build();

            ms = microserviceRepository.save(ms);
            microservices.add(ms);
        }

        return microservices;
    }

    private int countLinesOfCode(Path servicePath) {
        try (Stream<Path> files = Files.walk(servicePath)) {
            return files
                    .filter(p -> p.toString().endsWith(".java"))
                    .mapToInt(this::countLines)
                    .sum();
        } catch (IOException e) {
            return 0;
        }
    }

    private int countLines(Path file) {
        try {
            return (int) Files.lines(file, java.nio.charset.StandardCharsets.ISO_8859_1).count();
        } catch (IOException | UncheckedIOException e) {
            return 0;
        }
    }
}
