package com.saparate.pc.service;

import com.rate.resources.IResource;
import com.rate.user.services.GenericEnvironmentService;
import com.saparate.pc.repository.CBCEnvironmentRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CBCService extends GenericEnvironmentService {

    @Autowired
    private CBCEnvironmentRepo cbcEnvironmentRepo;

    @Override
    public List<? extends IResource> getResources(String tenantName, List<Long> envIds) {
        return cbcEnvironmentRepo.findByTenantNameAndIdIn(tenantName, envIds);
    }
}
