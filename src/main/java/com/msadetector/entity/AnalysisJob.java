package com.msadetector.entity;

import com.msadetector.enums.JobStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "analysis_jobs", indexes = {
    @Index(name = "idx_job_project", columnList = "project_id"),
    @Index(name = "idx_job_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisJob extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private JobStatus status = JobStatus.PENDING;

    @Column(name = "current_phase")
    private String currentPhase;

    @Column(name = "current_service")
    private String currentService;

    @Column(name = "services_completed")
    @Builder.Default
    private Integer servicesCompleted = 0;

    @Column(name = "total_services")
    @Builder.Default
    private Integer totalServices = 0;

    @Column(name = "progress_percentage")
    @Builder.Default
    private Integer progressPercentage = 0;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "analysis_number")
    @Builder.Default
    private Integer analysisNumber = 1;

    @Column(name = "run_designite")
    @Builder.Default
    private boolean runDesignite = true;

    @Column(name = "detect_cyclic_dependencies")
    @Builder.Default
    private boolean detectCyclicDependencies = true;

    @Column(name = "detect_shared_databases")
    @Builder.Default
    private boolean detectSharedDatabases = true;

    @Column(name = "detect_nano_services")
    @Builder.Default
    private boolean detectNanoServices = true;

    @Column(name = "detect_god_services")
    @Builder.Default
    private boolean detectGodServices = true;

    @Column(name = "detect_chatty_services")
    @Builder.Default
    private boolean detectChattyServices = true;

    @Column(name = "detect_hardcoded_endpoints")
    @Builder.Default
    private boolean detectHardcodedEndpoints = true;

    @Column(name = "detect_distributed_monoliths")
    @Builder.Default
    private boolean detectDistributedMonoliths = true;

    @Column(name = "detect_api_versioning_absence")
    @Builder.Default
    private boolean detectApiVersioningAbsence = true;

    @Column(name = "detect_wrong_cuts")
    @Builder.Default
    private boolean detectWrongCuts = true;

    @Column(name = "detect_esb_misuse")
    @Builder.Default
    private boolean detectEsbMisuse = true;

    @Column(name = "nano_service_max_loc")
    @Builder.Default
    private Integer nanoServiceMaxLoc = 500;

    @Column(name = "nano_service_max_endpoints")
    @Builder.Default
    private Integer nanoServiceMaxEndpoints = 2;

    @Column(name = "god_service_field_count")
    @Builder.Default
    private Integer godServiceFieldCount = 25;

    @Column(name = "god_service_public_methods")
    @Builder.Default
    private Integer godServicePublicMethods = 30;

    @Column(name = "god_service_loc")
    @Builder.Default
    private Integer godServiceLoc = 1000;

    @Column(name = "god_service_import_domains")
    @Builder.Default
    private Integer godServiceImportDomains = 20;

    @Column(name = "god_service_constructor_params")
    @Builder.Default
    private Integer godServiceConstructorParams = 12;

    @Column(name = "god_service_tcc_threshold")
    @Builder.Default
    private Double godServiceTccThreshold = 0.5;

    @Column(name = "god_service_min_metrics")
    @Builder.Default
    private Integer godServiceMinMetrics = 3;

    @Column(name = "god_service_min_domains")
    @Builder.Default
    private Integer godServiceMinDomains = 3;

    @Column(name = "chatty_service_min_calls")
    @Builder.Default
    private Integer chattyServiceMinCalls = 5;

    @Column(name = "esb_mediator_threshold")
    @Builder.Default
    private Double esbMediatorThreshold = 0.4;

    @Column(name = "distributed_monolith_high_coupling")
    @Builder.Default
    private Double distributedMonolithHighCoupling = 0.5;

    @Column(name = "distributed_monolith_connected_ratio")
    @Builder.Default
    private Double distributedMonolithConnectedRatio = 0.8;

    @Column(name = "distributed_monolith_moderate_coupling")
    @Builder.Default
    private Double distributedMonolithModerateCoupling = 0.3;

    @Builder.Default
    @OneToMany(mappedBy = "analysisJob", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Microservice> microservices = new ArrayList<>();

    @OneToOne(mappedBy = "analysisJob", cascade = CascadeType.ALL, orphanRemoval = true)
    private AnalysisResult result;

    public void start() {
        this.status = JobStatus.CLONING;
        this.startedAt = LocalDateTime.now();
    }

    public void complete(AnalysisResult result) {
        this.status = JobStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        this.progressPercentage = 100;
        this.result = result;
        result.setAnalysisJob(this);
    }

    public void fail(String errorMessage) {
        this.status = JobStatus.FAILED;
        this.completedAt = LocalDateTime.now();
        this.errorMessage = errorMessage;
    }

    public void cancel() {
        this.status = JobStatus.CANCELLED;
        this.completedAt = LocalDateTime.now();
    }

    public void updateProgress(JobStatus status, String phase, String currentService, int completed, int total) {
        this.status = status;
        this.currentPhase = phase;
        this.currentService = currentService;
        this.servicesCompleted = completed;
        this.totalServices = total;
        this.progressPercentage = total > 0 ? (completed * 100) / total : 0;
    }
}
