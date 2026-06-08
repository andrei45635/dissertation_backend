package com.msadetector.repository;

import com.msadetector.entity.Project;
import com.msadetector.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    Page<Project> findByOwner(User owner, Pageable pageable);

    List<Project> findByOwnerOrderByCreatedAtDesc(User owner);

    Optional<Project> findByIdAndOwner(Long id, User owner);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Project p WHERE p.id = :id AND p.owner = :owner")
    Optional<Project> findByIdAndOwnerForUpdate(@Param("id") Long id, @Param("owner") User owner);

    @Query("SELECT p FROM Project p LEFT JOIN FETCH p.microservices WHERE p.id = :id")
    Optional<Project> findByIdWithMicroservices(@Param("id") Long id);

    @Query("SELECT p FROM Project p LEFT JOIN FETCH p.analysisJobs WHERE p.id = :id AND p.owner = :owner")
    Optional<Project> findByIdAndOwnerWithJobs(@Param("id") Long id, @Param("owner") User owner);

    boolean existsByNameAndOwner(String name, User owner);
}
