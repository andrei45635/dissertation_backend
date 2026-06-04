package com.msadetector.repository;

import com.msadetector.entity.AnalysisJob;
import com.msadetector.entity.AnalysisResult;
import com.msadetector.entity.Project;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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

    Optional<AnalysisResult> findFirstByAnalysisJob_ProjectOrderByCreatedAtDesc(Project project);

    @Query("SELECT AVG(ar.healthScore) FROM AnalysisResult ar WHERE ar.analysisJob.project = :project")
    Double avgHealthScoreByProject(@Param("project") Project project);

    @EntityGraph(attributePaths = "detectedAntiPatterns")
    @Query("SELECT ar FROM AnalysisResult ar " +
           "WHERE ar.analysisJob.project = :project " +
           "AND ar.analysisJob.analysisNumber < :analysisNumber " +
           "ORDER BY ar.analysisJob.analysisNumber DESC")
    List<AnalysisResult> findPreviousResultsByAnalysisNumber(
            @Param("project") Project project,
            @Param("analysisNumber") Integer analysisNumber,
            Pageable pageable);

    @EntityGraph(attributePaths = "detectedAntiPatterns")
    @Query("SELECT ar FROM AnalysisResult ar " +
           "WHERE ar.analysisJob.project = :project " +
           "AND ar.createdAt < :createdBefore " +
           "ORDER BY ar.createdAt DESC")
    List<AnalysisResult> findPreviousResultsByCreatedAt(
            @Param("project") Project project,
            @Param("createdBefore") LocalDateTime createdBefore,
            Pageable pageable);

    @Query("SELECT ar FROM AnalysisResult ar JOIN FETCH ar.analysisJob LEFT JOIN FETCH ar.detectedAntiPatterns " +
           "WHERE ar.analysisJob.project = :project ORDER BY ar.createdAt DESC")
    List<AnalysisResult> findAllByProjectWithAntiPatterns(@Param("project") Project project);
}
