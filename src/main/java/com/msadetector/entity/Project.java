package com.msadetector.entity;

import com.msadetector.enums.SourceType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "projects", indexes = {
    @Index(name = "idx_project_owner", columnList = "owner_id"),
    @Index(name = "idx_project_name", columnList = "name")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project extends BaseEntity {

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false)
    private String name;

    @Size(max = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private SourceType sourceType;

    @Size(max = 500)
    @Column(name = "source_url")
    private String sourceUrl;

    @Size(max = 100)
    @Column(name = "branch")
    @Builder.Default
    private String branch = "main";

    @Column(name = "is_private")
    @Builder.Default
    private boolean isPrivate = false;

    @Column(name = "local_path")
    private String localPath;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Builder.Default
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Microservice> microservices = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AnalysisJob> analysisJobs = new ArrayList<>();

    public void addMicroservice(Microservice microservice) {
        microservices.add(microservice);
        microservice.setProject(this);
    }

    public void removeMicroservice(Microservice microservice) {
        microservices.remove(microservice);
        microservice.setProject(null);
    }

    public void addAnalysisJob(AnalysisJob job) {
        analysisJobs.add(job);
        job.setProject(this);
    }
}
