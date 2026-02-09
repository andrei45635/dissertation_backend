package com.msadetector.repository;

import com.msadetector.entity.AnalysisResult;
import com.msadetector.entity.DetectedAntiPattern;
import com.msadetector.entity.Project;
import com.msadetector.enums.AntiPatternType;
import com.msadetector.enums.Severity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DetectedAntiPatternRepository extends JpaRepository<DetectedAntiPattern, Long> {

    List<DetectedAntiPattern> findByAnalysisResult(AnalysisResult analysisResult);

    List<DetectedAntiPattern> findByAnalysisResultOrderBySeverityDesc(AnalysisResult analysisResult);

    List<DetectedAntiPattern> findByAnalysisResultAndPatternType(
        AnalysisResult analysisResult,
        AntiPatternType patternType
    );

    List<DetectedAntiPattern> findByAnalysisResultAndSeverity(
        AnalysisResult analysisResult,
        Severity severity
    );

    @Query("SELECT dap FROM DetectedAntiPattern dap WHERE dap.analysisResult.analysisJob.project = :project")
    List<DetectedAntiPattern> findByProject(@Param("project") Project project);

    @Query("SELECT dap.patternType, COUNT(dap) FROM DetectedAntiPattern dap " +
           "WHERE dap.analysisResult = :result GROUP BY dap.patternType ORDER BY COUNT(dap) DESC")
    List<Object[]> countByTypeForResult(@Param("result") AnalysisResult result);

    @Query("SELECT dap.severity, COUNT(dap) FROM DetectedAntiPattern dap " +
           "WHERE dap.analysisResult = :result GROUP BY dap.severity")
    List<Object[]> countBySeverityForResult(@Param("result") AnalysisResult result);

    @Query("SELECT COUNT(dap) FROM DetectedAntiPattern dap WHERE dap.analysisResult = :result")
    int countByAnalysisResult(@Param("result") AnalysisResult result);

    @Query("SELECT COUNT(dap) FROM DetectedAntiPattern dap WHERE dap.analysisResult = :result AND dap.severity = :severity")
    int countByAnalysisResultAndSeverity(
        @Param("result") AnalysisResult result,
        @Param("severity") Severity severity
    );
}
