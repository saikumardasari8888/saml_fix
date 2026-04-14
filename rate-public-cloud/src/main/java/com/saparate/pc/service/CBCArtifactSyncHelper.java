package com.saparate.pc.service;

import com.rate.commons.util.sync.IObjectSyncHelper;
import com.rate.user.entity.Customer;
import com.saparate.pc.entity.CBCArtifact;
import com.saparate.pc.entity.CBCArtifactVersion;
import lombok.AllArgsConstructor;

import java.util.Date;

@AllArgsConstructor
public class CBCArtifactSyncHelper implements IObjectSyncHelper<CBCArtifact> {

    private Customer userObj;

    @Override
    public boolean handleNewObjectSettings(CBCArtifact newObj) {
        CBCArtifactVersion newArtifactVer = newObj.getCbcArtifactVersion();
        newObj.setTenantName(userObj.getTenantName());
        Date dt = new Date();
        newObj.setCreatedBy(userObj.getEmail());
        newObj.setCreationDate(dt);
        newObj.setLastModifiedDate(dt);
        newObj.setLastModifiedBy(userObj.getEmail());
        newArtifactVer.setCreationDate(dt);
        newArtifactVer.setCreatedBy(userObj.getEmail());
        newArtifactVer.setTenantName(userObj.getTenantName());
        newArtifactVer.setLastModifiedDate(dt);
        newArtifactVer.setLastModifiedBy(userObj.getEmail());
        newArtifactVer.setVersion("1");
        return true;
    }

    @Override
    public boolean handleModificationSettings(CBCArtifact oldObj, CBCArtifact newObj) {
        Date dt = new Date();
        newObj.setRoId(oldObj.getRoId());
        newObj.setEnvId(oldObj.getEnvId());
        newObj.setCreationDate(oldObj.getCreationDate());
        newObj.setCreatedBy(oldObj.getCreatedBy());
        newObj.setTenantName(oldObj.getTenantName());
        newObj.setLastModifiedBy(userObj.getEmail());
        newObj.setLastModifiedDate(dt);
        CBCArtifactVersion newCBCArtifactversion = newObj.getCbcArtifactVersion();
        newCBCArtifactversion.setRoArtifactId(oldObj.getRoId());
        newCBCArtifactversion.setTenantName(newObj.getTenantName());
        CBCArtifactVersion oldCBCArtifactVersion = oldObj.getCbcArtifactVersion();
        newCBCArtifactversion.setCreatedBy(oldCBCArtifactVersion.getCreatedBy());
        newCBCArtifactversion.setCreationDate(oldCBCArtifactVersion.getCreationDate());
        int currentVersion = Integer.valueOf(oldCBCArtifactVersion.getVersion());
        if ( "TASK_COMPLETED".equalsIgnoreCase(oldCBCArtifactVersion.getStatus()) && !"TASK_COMPLETED".equalsIgnoreCase(newCBCArtifactversion.getStatus())) {
            currentVersion++;
            newCBCArtifactversion.setVersion(String.valueOf(currentVersion));
        } else {
            newCBCArtifactversion.setVersion(String.valueOf(currentVersion));
            newCBCArtifactversion.setRoVersionId(oldCBCArtifactVersion.getRoVersionId());
        }
        newCBCArtifactversion.setLastModifiedDate(dt);
        newCBCArtifactversion.setLastModifiedBy(newObj.getLastModifiedBy());
        return true;
    }

    @Override
    public boolean handleDeletionSettings(CBCArtifact oldObj) {
        return true;
    }
}
