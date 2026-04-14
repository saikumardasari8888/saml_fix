package com.saparate.pc.repository;

import com.rate.commons.util.sync.SyncStatus;
import com.saparate.pc.entity.PCArtifactSyncLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PCArtifactSyncLogRepo extends JpaRepository<PCArtifactSyncLog, Long> {

    List<PCArtifactSyncLog> findAllByTenantNameAndProjectIdOrderByCreationDateDesc(String tenantName, long projectId);

    PCArtifactSyncLog findByTenantNameAndId(String tenantName, long syncLogId);

    List<PCArtifactSyncLog> findAllByTenantNameAndEnvIdAndSyncStatus(String tenantName, long envId, SyncStatus syncStatus);

    List<PCArtifactSyncLog> findAllByTenantNameAndEnvIdAndProjectIdOrderByCreationDateDesc(String tenantName, long envId, long projectId);
}
