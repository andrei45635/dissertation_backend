package com.msadetector.entity;

import com.msadetector.enums.DependencyType;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;

@Entity
@Table(name = "service_dependencies", indexes = {
    @Index(name = "idx_dependency_source", columnList = "source_service_id"),
    @Index(name = "idx_dependency_target", columnList = "target_service_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceDependency extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_service_id", nullable = false)
    private Microservice sourceService;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_service_id", nullable = false)
    private Microservice targetService;

    @Enumerated(EnumType.STRING)
    @Column(name = "dependency_type", nullable = false)
    private DependencyType dependencyType;

    @Column(name = "call_count")
    @Builder.Default
    private Integer callCount = 1;

    @Size(max = 500)
    @Column(name = "evidence_file")
    private String evidenceFile;

    @Column(name = "evidence_line")
    private Integer evidenceLine;

    @Size(max = 1000)
    @Column(name = "evidence_code")
    private String evidenceCode;

    @Size(max = 500)
    @Column(name = "target_url")
    private String targetUrl;
}
