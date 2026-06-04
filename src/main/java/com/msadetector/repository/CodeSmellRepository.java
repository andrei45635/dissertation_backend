package com.msadetector.repository;

import com.msadetector.entity.CodeSmell;
import com.msadetector.entity.AnalysisJob;
import com.msadetector.entity.Microservice;
import com.msadetector.entity.Project;
import com.msadetector.enums.Severity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CodeSmellRepository extends JpaRepository<CodeSmell, Long> {

    List<CodeSmell> findByMicroservice(Microservice microservice);

    List<CodeSmell> findByMicroserviceOrderBySeverityDesc(Microservice microservice);

    List<CodeSmell> findByMicroserviceAndSeverity(Microservice microservice, Severity severity);

    @Query("SELECT cs FROM CodeSmell cs WHERE cs.microservice.project = :project")
    List<CodeSmell> findByProject(@Param("project") Project project);

    @Query("SELECT cs FROM CodeSmell cs WHERE cs.microservice.project = :project AND cs.severity = :severity")
    List<CodeSmell> findByProjectAndSeverity(
        @Param("project") Project project,
        @Param("severity") Severity severity
    );

    @Query("SELECT cs.smellType, COUNT(cs) FROM CodeSmell cs WHERE cs.microservice.project = :project GROUP BY cs.smellType ORDER BY COUNT(cs) DESC")
    List<Object[]> countByTypeForProject(@Param("project") Project project);

    @Query("SELECT cs.severity, COUNT(cs) FROM CodeSmell cs WHERE cs.microservice.project = :project GROUP BY cs.severity")
    List<Object[]> countBySeverityForProject(@Param("project") Project project);

    @Query("SELECT COUNT(cs) FROM CodeSmell cs WHERE cs.microservice = :microservice")
    int countByMicroservice(@Param("microservice") Microservice microservice);

    @Query("SELECT COUNT(cs) FROM CodeSmell cs WHERE cs.microservice.project = :project")
    int countByProject(@Param("project") Project project);

    @Query("SELECT COUNT(cs) FROM CodeSmell cs WHERE cs.microservice.analysisJob = :analysisJob")
    int countByAnalysisJob(@Param("analysisJob") AnalysisJob analysisJob);

    @Query("SELECT cs FROM CodeSmell cs WHERE cs.microservice.project = :project AND cs.smellType = 'God Class'")
    List<CodeSmell> findGodClassSmells(@Param("project") Project project);

    @Query("SELECT cs FROM CodeSmell cs WHERE cs.microservice.project = :project AND cs.smellType = 'Feature Envy'")
    List<CodeSmell> findFeatureEnvySmells(@Param("project") Project project);
}
