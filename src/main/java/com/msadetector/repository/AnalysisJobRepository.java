package com.msadetector.repository;

import com.msadetector.entity.AnalysisJob;
import com.msadetector.entity.Project;
import com.msadetector.entity.User;
import com.msadetector.enums.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, Long> {

    List<AnalysisJob> findByProject(Project project);

    List<AnalysisJob> findByProjectOrderByCreatedAtDesc(Project project);

    Page<AnalysisJob> findByProject(Project project, Pageable pageable);

    Optional<AnalysisJob> findFirstByProjectOrderByCreatedAtDesc(Project project);

    List<AnalysisJob> findByStatus(JobStatus status);

    @Query("SELECT j FROM AnalysisJob j WHERE j.project.owner = :owner ORDER BY j.createdAt DESC")
    Page<AnalysisJob> findByOwner(@Param("owner") User owner, Pageable pageable);

    @Query("SELECT j FROM AnalysisJob j LEFT JOIN FETCH j.result WHERE j.id = :id")
    Optional<AnalysisJob> findByIdWithResult(@Param("id") Long id);

    @Query("SELECT j FROM AnalysisJob j WHERE j.id = :id AND j.project.owner = :owner")
    Optional<AnalysisJob> findByIdAndOwner(@Param("id") Long id, @Param("owner") User owner);

    @Query("SELECT j FROM AnalysisJob j LEFT JOIN FETCH j.result WHERE j.id = :id AND j.project.owner = :owner")
    Optional<AnalysisJob> findByIdAndOwnerWithResult(@Param("id") Long id, @Param("owner") User owner);

    @Query("SELECT j FROM AnalysisJob j WHERE j.status = 'PENDING' ORDER BY j.createdAt ASC")
    List<AnalysisJob> findPendingJobs();

    @Query("SELECT j FROM AnalysisJob j WHERE j.status IN ('CLONING', 'DETECTING_SERVICES', 'ANALYZING_SERVICES', 'BUILDING_GRAPH', 'DETECTING_PATTERNS')")
    List<AnalysisJob> findRunningJobs();

    @Query("SELECT j.status, COUNT(j) FROM AnalysisJob j WHERE j.project = :project GROUP BY j.status")
    List<Object[]> countByStatusForProject(@Param("project") Project project);
}
