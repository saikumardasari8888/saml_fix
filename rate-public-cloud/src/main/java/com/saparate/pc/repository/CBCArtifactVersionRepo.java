package com.saparate.pc.repository;

import com.saparate.pc.entity.CBCArtifactVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.transaction.Transactional;

public interface CBCArtifactVersionRepo extends JpaRepository<CBCArtifactVersion, Long> {

    CBCArtifactVersion findByTenantNameAndProjectIdAndRoArtifactIdAndRoVersionId(String tenantName, long projectId, long roArtifactId, long roVersionId);

    @Transactional
    @Modifying
    @Query("UPDATE CBCArtifactVersion SET status = :status WHERE roVersionId=:versionId and tenantName=:tenantName")
    int updateStatus(@Param(value = "status") String status, @Param(value = "versionId") long versionId, @Param(value = "tenantName") String tenantName);

}
