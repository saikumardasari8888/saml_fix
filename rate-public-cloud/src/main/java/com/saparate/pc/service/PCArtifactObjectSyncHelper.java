package com.saparate.pc.service;

import com.rate.commons.util.sync.IObjectSyncHelper;
import com.rate.user.entity.Customer;
import com.saparate.pc.entity.PCArtifact;
import com.saparate.pc.entity.PCArtifactVersion;
import lombok.AllArgsConstructor;

import java.util.Date;

@AllArgsConstructor
public class PCArtifactObjectSyncHelper implements IObjectSyncHelper<PCArtifact> {

    private Customer userObj;
    @Override
    public boolean handleNewObjectSettings(PCArtifact newObj) {
        PCArtifactVersion newArtifactVer = newObj.getPcArtifactVersion();
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
        return true;
    }

    @Override
    public boolean handleModificationSettings(PCArtifact oldObj, PCArtifact newObj) {
        Date dt = new Date();
        newObj.setRoId(oldObj.getRoId());
        newObj.setEnvId(oldObj.getEnvId());
        newObj.setCreationDate(oldObj.getCreationDate());
        newObj.setCreatedBy(oldObj.getCreatedBy());
        newObj.setTenantName(oldObj.getTenantName());
        newObj.setLastModifiedBy(userObj.getEmail());
        newObj.setLastModifiedDate(dt);
        PCArtifactVersion newArtifactVer = newObj.getPcArtifactVersion();
        PCArtifactVersion oldArtifactVer = oldObj.getPcArtifactVersion();
        if ( oldArtifactVer.getVersion().equals(newArtifactVer.getVersion()) ) {
            newArtifactVer.setRoVersionId(oldArtifactVer.getRoVersionId());
            newArtifactVer.setCreatedBy(oldArtifactVer.getCreatedBy());
            newArtifactVer.setCreationDate(oldArtifactVer.getCreationDate());
        } else {
            newArtifactVer.setCreationDate(dt);
            newArtifactVer.setCreatedBy(userObj.getEmail());
        }
        newArtifactVer.setTenantName(userObj.getTenantName());
        newArtifactVer.setLastModifiedDate(dt);
        newArtifactVer.setLastModifiedBy(userObj.getEmail());
        newArtifactVer.setRoArtifactId(oldObj.getRoId());
        return true;
    }

    @Override
    public boolean handleDeletionSettings(PCArtifact oldObj) {
        return true;
    }
}
