package com.saparate.pc.repository;

import com.rate.commons.util.sync.SyncStatus;
import com.saparate.pc.entity.CBCArtifactSyncLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CBCAtifactSyncLogRepo extends JpaRepository<CBCArtifactSyncLog, Long> {

    List<CBCArtifactSyncLog> findAllByTenantNameAndProjectIdOrderByCreationDateDesc(String tenantName, long projectId);

    CBCArtifactSyncLog findByTenantNameAndId(String tenantName, long syncLogId);

    List<CBCArtifactSyncLog> findAllByTenantNameAndEnvIdAndSyncStatus(String tenantName, long envId, SyncStatus syncStatus);

    List<CBCArtifactSyncLog> findAllByTenantNameAndEnvIdAndProjectIdOrderByCreationDateDesc(String tenantName, long envId, long projectId);
}
