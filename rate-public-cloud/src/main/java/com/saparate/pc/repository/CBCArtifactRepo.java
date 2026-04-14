package com.saparate.pc.repository;

import com.saparate.pc.entity.CBCArtifact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CBCArtifactRepo extends JpaRepository<CBCArtifact, Long> {

    List<CBCArtifact> findAllByTenantNameAndProjectIdOrderByCreationDateDesc(String tenantName, long projectId);

    CBCArtifact findByTenantNameAndRoId(String tenantName, long syncLogId);

    List<CBCArtifact> findByTenantNameAndProjectIdAndEnvIdAndIsDeleted(String tenantName, long projectId, long envId, boolean isDeleted);
}
