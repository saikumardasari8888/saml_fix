package com.saparate.pc.repository;

import com.saparate.pc.entity.PCEnvironment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface PCEnvironmentRepo extends JpaRepository<PCEnvironment, Long> {

    Optional<PCEnvironment> findByTenantNameAndId(String tenantName, long id);
    List<PCEnvironment> findAllByTenantName(String tenantName);

    List<PCEnvironment> findByTenantNameAndIdIn(String tenantName, List<Long> ids);

    @Transactional
    void deleteByTenantNameAndId(String tenantName, long id);
}
