package com.msadetector.repository;

import com.msadetector.entity.Endpoint;
import com.msadetector.entity.Microservice;
import com.msadetector.enums.HttpMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EndpointRepository extends JpaRepository<Endpoint, Long> {

    List<Endpoint> findByMicroservice(Microservice microservice);

    List<Endpoint> findByMicroserviceOrderByPathAsc(Microservice microservice);

    @Query("SELECT e FROM Endpoint e WHERE e.microservice = :microservice AND e.httpMethod = :method")
    List<Endpoint> findByMicroserviceAndMethod(
        @Param("microservice") Microservice microservice,
        @Param("method") HttpMethod method
    );

    @Query("SELECT COUNT(e) FROM Endpoint e WHERE e.microservice = :microservice")
    int countByMicroservice(@Param("microservice") Microservice microservice);

    @Query("SELECT e FROM Endpoint e WHERE e.microservice.project.id = :projectId AND e.hasVersioning = false")
    List<Endpoint> findEndpointsWithoutVersioning(@Param("projectId") Long projectId);

    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END FROM Endpoint e WHERE e.microservice = :microservice AND e.hasVersioning = true")
    boolean hasVersionedEndpoints(@Param("microservice") Microservice microservice);
}
