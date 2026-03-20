package com.msadetector.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "analysis_results", indexes = {
    @Index(name = "idx_result_job", columnList = "analysis_job_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisResult extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_job_id", nullable = false)
    private AnalysisJob analysisJob;

    @Column(name = "health_score")
    @Builder.Default
    private Integer healthScore = 100;

    @Column(name = "services_analyzed")
    @Builder.Default
    private Integer servicesAnalyzed = 0;

    @Column(name = "total_anti_patterns")
    @Builder.Default
    private Integer totalAntiPatterns = 0;

    @Column(name = "total_code_smells")
    @Builder.Default
    private Integer totalCodeSmells = 0;

    @Column(name = "critical_issues")
    @Builder.Default
    private Integer criticalIssues = 0;

    @Column(name = "high_issues")
    @Builder.Default
    private Integer highIssues = 0;

    @Column(name = "medium_issues")
    @Builder.Default
    private Integer mediumIssues = 0;

    @Column(name = "low_issues")
    @Builder.Default
    private Integer lowIssues = 0;

    @Column(name = "total_lines_of_code")
    @Builder.Default
    private Integer totalLinesOfCode = 0;

    @Column(name = "average_service_size")
    @Builder.Default
    private Double averageServiceSize = 0.0;

    @Column(name = "total_dependencies")
    @Builder.Default
    private Integer totalDependencies = 0;

    @Column(name = "cycle_count")
    @Builder.Default
    private Integer cycleCount = 0;

    @Column(name = "coupling_coefficient")
    @Builder.Default
    private Double couplingCoefficient = 0.0;

    @Column(name = "dependency_graph_json", columnDefinition = "TEXT")
    private String dependencyGraphJson;

    @Builder.Default
    @OneToMany(mappedBy = "analysisResult", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetectedAntiPattern> detectedAntiPatterns = new ArrayList<>();

    public void addAntiPattern(DetectedAntiPattern antiPattern) {
        detectedAntiPatterns.add(antiPattern);
        antiPattern.setAnalysisResult(this);
    }

    /**
     * Quick legacy calculation — kept as a fallback.
     * The detailed breakdown is computed by {@code HealthScoreCalculator}.
     */
    public void calculateHealthScore() {
        int score = 100;
        score -= criticalIssues * 15;
        score -= highIssues * 10;
        score -= mediumIssues * 5;
        score -= lowIssues * 2;
        this.healthScore = Math.max(0, score);
    }
}
