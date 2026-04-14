package com.saparate.pc.repository;

import com.saparate.pc.entity.CBCEnvironment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface CBCEnvironmentRepo extends JpaRepository<CBCEnvironment, Long> {

    Optional<CBCEnvironment> findByTenantNameAndId(String tenantName, long id);

    List<CBCEnvironment> findAllByTenantName(String tenantName);

    List<CBCEnvironment> findByTenantNameAndIdIn(String tenantName, List<Long> ids);

    @Transactional
    void deleteByTenantNameAndId(String tenantName, long id);
}
