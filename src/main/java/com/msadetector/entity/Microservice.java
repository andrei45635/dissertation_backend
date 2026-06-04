package com.msadetector.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "microservices", indexes = {
    @Index(name = "idx_microservice_project", columnList = "project_id"),
    @Index(name = "idx_microservice_analysis_job", columnList = "analysis_job_id"),
    @Index(name = "idx_microservice_name", columnList = "name")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Microservice extends BaseEntity {

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false)
    private String name;

    @Size(max = 500)
    @Column(name = "relative_path")
    private String relativePath;

    @Column(name = "lines_of_code")
    @Builder.Default
    private Integer linesOfCode = 0;

    @Column(name = "number_of_classes")
    @Builder.Default
    private Integer numberOfClasses = 0;

    @Column(name = "number_of_endpoints")
    @Builder.Default
    private Integer numberOfEndpoints = 0;

    @Column(name = "number_of_entities")
    @Builder.Default
    private Integer numberOfEntities = 0;

    @Size(max = 20)
    @Column(name = "detection_confidence")
    private String detectionConfidence;

    @Size(max = 100)
    @Column(name = "detection_signal")
    private String detectionSignal;

    @Size(max = 500)
    @Column(name = "datasource_url")
    private String datasourceUrl;

    @Size(max = 255)
    @Column(name = "datasource_name")
    private String datasourceName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_job_id")
    private AnalysisJob analysisJob;

    @Builder.Default
    @OneToMany(mappedBy = "microservice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Endpoint> endpoints = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "sourceService", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ServiceDependency> outgoingDependencies = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "targetService", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ServiceDependency> incomingDependencies = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "microservice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CodeSmell> codeSmells = new ArrayList<>();

    public void addEndpoint(Endpoint endpoint) {
        endpoints.add(endpoint);
        endpoint.setMicroservice(this);
    }

    public void addOutgoingDependency(ServiceDependency dependency) {
        outgoingDependencies.add(dependency);
        dependency.setSourceService(this);
    }

    public void addCodeSmell(CodeSmell smell) {
        codeSmells.add(smell);
        smell.setMicroservice(this);
    }
}
