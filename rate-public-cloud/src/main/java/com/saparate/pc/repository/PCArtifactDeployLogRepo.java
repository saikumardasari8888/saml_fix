package com.saparate.pc.repository;

import com.saparate.pc.entity.PCArtifactDeployLog;
import com.saparate.pc.enums.PCArtifactDeployLogStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.transaction.Transactional;
import java.util.Date;
import java.util.List;

@Repository
public interface PCArtifactDeployLogRepo extends JpaRepository<PCArtifactDeployLog, Long>  {

    List<PCArtifactDeployLog> findAllByTenantNameAndWfTaskIdOrderByIdAsc(String tenantName, String wfTaskId);

    PCArtifactDeployLog findTopByTenantNameAndProjectIdAndPcDeployEnvIdAndRoPcArtifactVersionIdOrderByIdDesc(String tenantName, long projectId, long envId, long roVersionId);

    List<PCArtifactDeployLog> findAllByTenantNameAndProjectIdAndWfTaskIdAndDeployStatus(String tenantName, long projectId, String wfTaskId, PCArtifactDeployLogStatus deployStatus);
    @Transactional
    @Modifying
    @Query("update PCArtifactDeployLog pcLog set pcLog.deployedType =:deployedType, pcLog.manualDeployedBy =:manualDeployedBy, pcLog.manualDeployedReason = :manualDeployedReason, pcLog.manualDeployDate = :manualDeployDate, pcLog.lastModifiedDate = :lastModifiedDate, pcLog.lastModifiedBy = :lastModifiedBy " +
            "where pcLog.id = :id")
    int updateDeployLogManual(@Param(value = "id") long logId, @Param(value = "deployedType") String deployedType,
                              @Param(value = "manualDeployedBy") String manualDeployedBy, @Param(value = "manualDeployedReason") String manualDeployedReason, @Param(value = "manualDeployDate") Date manualDeployDate, @Param(value = "lastModifiedDate") Date lastModifiedDate, @Param(value = "lastModifiedBy") String lastModifiedBy);

}
