package com.saparate.pc.repository;

import com.saparate.pc.entity.PCArtifact;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PCArtifactRepo extends JpaRepository<PCArtifact, Long> {

    List<PCArtifact> findAllByTenantNameAndProjectIdOrderByCreationDateDesc(String tenantName, long projectId);

    PCArtifact findByTenantNameAndProjectIdAndRoId(String tenantName, long projectId,  long roArtifactId);

    Page<PCArtifact> findByTenantNameAndProjectIdAndEnvIdAndIsDeletedOrderByRoIdDesc(String tenantName, long projectId, long envId, boolean isDeleted, Pageable pageable);

    List<PCArtifact> findByTenantNameAndProjectIdAndEnvIdAndIsDeletedOrderByRoIdDesc(String tenantName, long projectId, long envId, boolean isDeleted);

    PCArtifact findByTenantNameAndProjectIdAndEnvIdAndArtifactIdAndIsDeleted(String tenantName, long projectId, long envId, String sapTransportId, boolean isDeleted);
}
