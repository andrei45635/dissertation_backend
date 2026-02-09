package com.msadetector.repository;

import com.msadetector.entity.Microservice;
import com.msadetector.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MicroserviceRepository extends JpaRepository<Microservice, Long> {

    List<Microservice> findByProject(Project project);

    List<Microservice> findByProjectOrderByNameAsc(Project project);

    Optional<Microservice> findByProjectAndName(Project project, String name);

    @Query("SELECT m FROM Microservice m LEFT JOIN FETCH m.endpoints WHERE m.project = :project")
    List<Microservice> findByProjectWithEndpoints(@Param("project") Project project);

    @Query("SELECT m FROM Microservice m LEFT JOIN FETCH m.outgoingDependencies WHERE m.project = :project")
    List<Microservice> findByProjectWithDependencies(@Param("project") Project project);

    @Query("SELECT m FROM Microservice m " +
           "LEFT JOIN FETCH m.endpoints " +
           "LEFT JOIN FETCH m.codeSmells " +
           "WHERE m.id = :id")
    Optional<Microservice> findByIdWithDetails(@Param("id") Long id);

    @Query("SELECT COUNT(m) FROM Microservice m WHERE m.project = :project")
    int countByProject(@Param("project") Project project);

    @Query("SELECT SUM(m.linesOfCode) FROM Microservice m WHERE m.project = :project")
    Integer sumLinesOfCodeByProject(@Param("project") Project project);

    @Query("SELECT AVG(m.linesOfCode) FROM Microservice m WHERE m.project = :project")
    Double avgLinesOfCodeByProject(@Param("project") Project project);

    @Query("SELECT m FROM Microservice m WHERE m.project = :project AND m.datasourceUrl = :datasourceUrl")
    List<Microservice> findByProjectAndDatasourceUrl(@Param("project") Project project, @Param("datasourceUrl") String datasourceUrl);

    @Query("SELECT m FROM Microservice m WHERE m.project = :project AND m.linesOfCode < :maxLoc AND m.numberOfEndpoints <= :maxEndpoints")
    List<Microservice> findPotentialNanoServices(
        @Param("project") Project project,
        @Param("maxLoc") int maxLoc,
        @Param("maxEndpoints") int maxEndpoints
    );
}
