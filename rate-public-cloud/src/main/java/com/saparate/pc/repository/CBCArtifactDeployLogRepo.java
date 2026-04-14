package com.saparate.pc.repository;

import com.saparate.pc.entity.CBCArtifactDeployLog;
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
public interface CBCArtifactDeployLogRepo extends JpaRepository<CBCArtifactDeployLog, Long> {
    List<CBCArtifactDeployLog> findAllByTenantNameAndWfTaskIdOrderByIdAsc(String tenantName, String wfTaskId);

    CBCArtifactDeployLog findTopByTenantNameAndProjectIdAndCbcDeployEnvIdAndRoCbcArtifactVersionIdOrderByIdDesc(String tenantName, long projectId, long envId, long roVersionId);

    CBCArtifactDeployLog findTopByTenantNameAndProjectIdAndRoCbcArtifactVersionIdOrderByIdDesc(String tenantName, long projectId, long roVersionId);


    List<CBCArtifactDeployLog> findAllByTenantNameAndProjectIdAndWfTaskIdAndDeployStatus(String tenantName, long projectId, String wfTaskId, PCArtifactDeployLogStatus deployStatus);

    @Transactional
    @Modifying
    @Query("update CBCArtifactDeployLog cbcLog set cbcLog.deployedType =:deployedType, cbcLog.manualDeployedBy =:manualDeployedBy, cbcLog.manualDeployedReason = :manualDeployedReason, cbcLog.manualDeployDate = :manualDeployDate, cbcLog.lastModifiedDate = :lastModifiedDate, cbcLog.lastModifiedBy = :lastModifiedBy " +
            "where cbcLog.id = :id")
    int updateDeployLogManual(@Param(value = "id") long logId, @Param(value = "deployedType") String deployedType,
                              @Param(value = "manualDeployedBy") String manualDeployedBy, @Param(value = "manualDeployedReason") String manualDeployedReason, @Param(value = "manualDeployDate") Date manualDeployDate, @Param(value = "lastModifiedDate") Date lastModifiedDate, @Param(value = "lastModifiedBy") String lastModifiedBy);

}
