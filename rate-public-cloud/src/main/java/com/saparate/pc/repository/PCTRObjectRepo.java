package com.saparate.pc.repository;

import com.saparate.pc.entity.PCTRObject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PCTRObjectRepo extends JpaRepository<PCTRObject, Long> {

    PCTRObject findByTenantNameAndProjectIdAndRoArtifactId(String tenantName, long projectId, long roArtVersionId);
}
