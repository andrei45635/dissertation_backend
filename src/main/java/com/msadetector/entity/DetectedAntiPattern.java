package com.msadetector.entity;

import com.msadetector.enums.AntiPatternType;
import com.msadetector.enums.Severity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;

@Entity
@Table(name = "detected_anti_patterns", indexes = {
    @Index(name = "idx_antipattern_result", columnList = "analysis_result_id"),
    @Index(name = "idx_antipattern_type", columnList = "pattern_type"),
    @Index(name = "idx_antipattern_severity", columnList = "severity")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DetectedAntiPattern extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_result_id", nullable = false)
    private AnalysisResult analysisResult;

    @Enumerated(EnumType.STRING)
    @Column(name = "pattern_type", nullable = false)
    private AntiPatternType patternType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Severity severity = Severity.MEDIUM;

    @Size(max = 1000)
    private String description;

    @Column(name = "affected_services", columnDefinition = "TEXT")
    private String affectedServicesJson;

    @Column(name = "details_json", columnDefinition = "TEXT")
    private String detailsJson;

    @Size(max = 2000)
    private String remediation;

    @Column(name = "evidence_json", columnDefinition = "TEXT")
    private String evidenceJson;

    @Column(name = "cycle_length")
    private Integer cycleLength;

    @Size(max = 500)
    @Column(name = "shared_database_url")
    private String sharedDatabaseUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_service_id")
    private Microservice primaryService;

    @Column(name = "call_count")
    private Integer callCount;
}
