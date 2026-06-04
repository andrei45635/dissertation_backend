package com.msadetector.repository;

import com.msadetector.entity.Microservice;
import com.msadetector.entity.Project;
import com.msadetector.entity.AnalysisJob;
import com.msadetector.entity.ServiceDependency;
import com.msadetector.enums.DependencyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceDependencyRepository extends JpaRepository<ServiceDependency, Long> {

    List<ServiceDependency> findBySourceService(Microservice sourceService);

    List<ServiceDependency> findByTargetService(Microservice targetService);

    Optional<ServiceDependency> findBySourceServiceAndTargetService(
        Microservice sourceService,
        Microservice targetService
    );

    @Query("SELECT sd FROM ServiceDependency sd WHERE sd.sourceService.project = :project")
    List<ServiceDependency> findByProject(@Param("project") Project project);

    @Query("SELECT sd FROM ServiceDependency sd WHERE sd.sourceService.analysisJob = :analysisJob")
    List<ServiceDependency> findByAnalysisJob(@Param("analysisJob") AnalysisJob analysisJob);

    @Query("SELECT sd FROM ServiceDependency sd " +
           "JOIN FETCH sd.sourceService " +
           "JOIN FETCH sd.targetService " +
           "WHERE sd.sourceService.project = :project")
    List<ServiceDependency> findByProjectWithServices(@Param("project") Project project);

    @Query("SELECT sd FROM ServiceDependency sd " +
           "JOIN FETCH sd.sourceService " +
           "JOIN FETCH sd.targetService " +
           "WHERE sd.sourceService.analysisJob = :analysisJob")
    List<ServiceDependency> findByAnalysisJobWithServices(@Param("analysisJob") AnalysisJob analysisJob);

    @Query("SELECT sd FROM ServiceDependency sd WHERE sd.sourceService.project = :project AND sd.dependencyType = :type")
    List<ServiceDependency> findByProjectAndType(
        @Param("project") Project project,
        @Param("type") DependencyType type
    );

    @Query("SELECT COUNT(sd) FROM ServiceDependency sd WHERE sd.sourceService.project = :project")
    int countByProject(@Param("project") Project project);

    @Query("SELECT COUNT(sd) FROM ServiceDependency sd WHERE sd.sourceService.analysisJob = :analysisJob")
    int countByAnalysisJob(@Param("analysisJob") AnalysisJob analysisJob);

    @Query("SELECT sd FROM ServiceDependency sd WHERE sd.sourceService.project = :project AND sd.callCount >= :minCalls")
    List<ServiceDependency> findChattyDependencies(
        @Param("project") Project project,
        @Param("minCalls") int minCalls
    );

    @Query("SELECT sd FROM ServiceDependency sd WHERE sd.sourceService.analysisJob = :analysisJob AND sd.callCount >= :minCalls")
    List<ServiceDependency> findChattyDependenciesByAnalysisJob(
        @Param("analysisJob") AnalysisJob analysisJob,
        @Param("minCalls") int minCalls
    );

    @Query("SELECT COUNT(sd) FROM ServiceDependency sd WHERE sd.sourceService = :service")
    int countOutgoingDependencies(@Param("service") Microservice service);

    @Query("SELECT COUNT(sd) FROM ServiceDependency sd WHERE sd.targetService = :service")
    int countIncomingDependencies(@Param("service") Microservice service);

    @Query("SELECT sd.targetService, COUNT(sd) as cnt FROM ServiceDependency sd " +
           "WHERE sd.sourceService.project = :project " +
           "GROUP BY sd.targetService " +
           "ORDER BY cnt DESC")
    List<Object[]> findMostCalledServices(@Param("project") Project project);
}
