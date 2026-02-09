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
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
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

    public AnalysisWorker(
            AnalysisJobRepository jobRepository,
            ProjectRepository projectRepository,
            MicroserviceRepository microserviceRepository,
            DesigniteService designiteService,
            MicroserviceDetector microserviceDetector,
            AntiPatternDetectorService antiPatternDetector
    ) {
        this.jobRepository = jobRepository;
        this.projectRepository = projectRepository;
        this.microserviceRepository = microserviceRepository;
        this.designiteService = designiteService;
        this.microserviceDetector = microserviceDetector;
        this.antiPatternDetector = antiPatternDetector;
    }

    @Async
    @Transactional
    public void processJob(Long jobId) {
        AnalysisJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.error("Job not found: {}", jobId);
            return;
        }

        try {
            job.start();
            jobRepository.save(job);

            Project project = job.getProject();
            Path projectPath = Path.of(project.getLocalPath());

            updateJobStatus(job, JobStatus.DETECTING_SERVICES, "Scanning for microservices", null, 0, 0);
            List<Microservice> microservices = detectMicroservices(project, projectPath);

            int total = microservices.size();
            int completed = 0;

            updateJobStatus(job, JobStatus.ANALYZING_SERVICES, "Running code analysis", null, completed, total);

            for (Microservice ms : microservices) {
                updateJobStatus(job, JobStatus.ANALYZING_SERVICES, "Analyzing service", ms.getName(), completed, total);

                if (job.isRunDesignite()) {
                    Path servicePath = projectPath.resolve(ms.getRelativePath());
                    designiteService.analyzeService(ms, servicePath);
                }

                completed++;
                updateJobStatus(job, JobStatus.ANALYZING_SERVICES, "Analyzing service", ms.getName(), completed, total);
            }

            updateJobStatus(job, JobStatus.BUILDING_GRAPH, "Building dependency graph", null, completed, total);
            antiPatternDetector.buildDependencyGraph(project);

            updateJobStatus(job, JobStatus.DETECTING_PATTERNS, "Detecting anti-patterns", null, completed, total);
            AnalysisResult result = antiPatternDetector.detectAntiPatterns(project, job);

            job.complete(result);
            jobRepository.save(job);

            log.info("Analysis completed for job {}", jobId);

        } catch (Exception e) {
            log.error("Analysis failed for job {}", jobId, e);
            job.fail(e.getMessage());
            jobRepository.save(job);
        }
    }

    private List<Microservice> detectMicroservices(Project project, Path projectPath) {
        List<Path> servicePaths = microserviceDetector.detectServices(projectPath);
        List<Microservice> microservices = new ArrayList<>();

        for (Path servicePath : servicePaths) {
            String name = servicePath.getFileName().toString();
            String relativePath = projectPath.relativize(servicePath).toString();

            Microservice ms = Microservice.builder()
                    .name(name)
                    .relativePath(relativePath)
                    .project(project)
                    .linesOfCode(countLinesOfCode(servicePath))
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
            return (int) Files.lines(file).count();
        } catch (IOException e) {
            return 0;
        }
    }

    private void updateJobStatus(AnalysisJob job, JobStatus status, String phase, String currentService, int completed, int total) {
        job.updateProgress(status, phase, currentService, completed, total);
        jobRepository.save(job);
    }
}
