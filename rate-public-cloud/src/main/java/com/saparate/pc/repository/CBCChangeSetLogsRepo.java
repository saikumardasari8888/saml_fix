package com.saparate.pc.repository;

import tools.jackson.databind.JsonNode;
import com.saparate.pc.entity.CBCChangeSetLogs;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.transaction.Transactional;

public interface CBCChangeSetLogsRepo extends JpaRepository<CBCChangeSetLogs, Long> {

    CBCChangeSetLogs findByTenantNameAndProjectIdAndRoArtifactIdAndRoVersionId(String tenantName, long projectId, long roArtifactId, long roVersionId);
}
