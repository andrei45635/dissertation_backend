package com.msadetector.repository;

import com.msadetector.entity.AnalysisJob;
import com.msadetector.entity.AnalysisResult;
import com.msadetector.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, Long> {

    Optional<AnalysisResult> findByAnalysisJob(AnalysisJob analysisJob);

    @Query("SELECT ar FROM AnalysisResult ar LEFT JOIN FETCH ar.detectedAntiPatterns WHERE ar.analysisJob = :job")
    Optional<AnalysisResult> findByAnalysisJobWithAntiPatterns(@Param("job") AnalysisJob job);

    @Query("SELECT ar FROM AnalysisResult ar LEFT JOIN FETCH ar.detectedAntiPatterns WHERE ar.id = :id")
    Optional<AnalysisResult> findByIdWithAntiPatterns(@Param("id") Long id);

    @Query("SELECT ar FROM AnalysisResult ar WHERE ar.analysisJob.project = :project ORDER BY ar.createdAt DESC")
    List<AnalysisResult> findByProjectOrderByCreatedAtDesc(@Param("project") Project project);

    @Query("SELECT ar FROM AnalysisResult ar WHERE ar.analysisJob.project = :project ORDER BY ar.createdAt DESC LIMIT 1")
    Optional<AnalysisResult> findLatestByProject(@Param("project") Project project);

    @Query("SELECT AVG(ar.healthScore) FROM AnalysisResult ar WHERE ar.analysisJob.project = :project")
    Double avgHealthScoreByProject(@Param("project") Project project);
}
