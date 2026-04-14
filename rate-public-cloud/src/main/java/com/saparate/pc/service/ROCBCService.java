package com.saparate.pc.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import tools.jackson.databind.JsonNode;
import com.rate.commons.DateUtil;
import com.rate.commons.util.sync.SyncObjectsComparator;
import com.rate.commons.util.sync.SyncObjectsResult;
import com.rate.commons.util.sync.SyncStatus;
import com.rate.user.entity.Credential;
import com.rate.user.entity.Customer;
import com.rate.user.services.CredentialService;
import com.saparate.pc.client.CBCBrowserClient;
import com.saparate.pc.config.PCConnConfig;
import com.saparate.pc.entity.CBCArtifact;
import com.saparate.pc.entity.CBCArtifactDeployLog;
import com.saparate.pc.entity.CBCArtifactSyncLog;
import com.saparate.pc.entity.CBCArtifactVersion;
import com.saparate.pc.entity.CBCChangeSetLogs;
import com.saparate.pc.entity.CBCEnvironment;
import com.saparate.pc.enums.PCArtifactDeployLogStatus;
import com.saparate.pc.exception.ROPublicCloudException;
import com.saparate.pc.model.CBCTargetSystemDetails;
import com.saparate.pc.model.CBCWorkSpace;
import com.saparate.pc.model.RuntimeImportArtifact;
import com.saparate.pc.repository.CBCArtifactDeployLogRepo;
import com.saparate.pc.repository.CBCArtifactRepo;
import com.saparate.pc.repository.CBCArtifactVersionRepo;
import com.saparate.pc.repository.CBCAtifactSyncLogRepo;
import com.saparate.pc.repository.CBCChangeSetLogsRepo;
import com.saparate.pc.repository.CBCEnvironmentRepo;
import com.saparate.pc.repository.CBCMysqlDAO;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@Getter
@Service
public class ROCBCService {
    @Autowired
    private CBCEnvironmentRepo cbcEnvironmentRepo;
    @Autowired
    private CBCAtifactSyncLogRepo cbcAtifactSyncLogRepo;
    @Autowired
    private CredentialService credentialService;
    @Autowired
    private SAPPublicCloudService sapPublicCloudService;
    @Autowired
    private CBCArtifactRepo cbcArtifactRepo;
    @Autowired
    private CBCArtifactVersionRepo cbcArtifactVersionRepo;
    @Autowired
    private CBCMysqlDAO cbcMysqlDAO;
    @Autowired
    private CBCArtifactDeployLogRepo cbcArtifactDeployLogRepo;
    @Autowired
    private CBCChangeSetLogsRepo cbcChangeSetLogsRepo;

    public CBCEnvironment addOrUpdateSAPCBCEnv(CBCEnvironment sapcbcEnv, Customer userObj) throws URISyntaxException, JsonProcessingException {

        String userName = userObj.getUsername();
        Date currentDate = new Date();
        sapcbcEnv.setLastModifiedBy(userName);
        sapcbcEnv.setLastModifiedDate(currentDate);
        CBCEnvironment existsapcbcenv = null;
        if (sapcbcEnv.getId() > 0) {
             existsapcbcenv = cbcEnvironmentRepo.findByTenantNameAndId(userObj.getTenantName(), sapcbcEnv.getId()).orElse(null);
            if (existsapcbcenv != null) {
                sapcbcEnv.setCreatedBy(existsapcbcenv.getCreatedBy());
                sapcbcEnv.setCreationDate(existsapcbcenv.getCreationDate());
            } else {
                log.warn("Record not found for  addOrUpdate id : {}", sapcbcEnv.getId());
                throw new IllegalArgumentException(String.format("SAP CBC Environment data was not found for id : %s", sapcbcEnv.getId()));
            }
        } else {
            sapcbcEnv.setCreatedBy(userName);
            sapcbcEnv.setCreationDate(currentDate);
        }
        if (existsapcbcenv == null || existsapcbcenv.getCbcTargetSystemDetails() == null || !(existsapcbcenv.getWorkSpaceId().equalsIgnoreCase(sapcbcEnv.getWorkSpaceId()))) {
            CBCBrowserClient cbcBrowserClient = new CBCBrowserClient();
            Credential cred = this.credentialService.getCredentialById(sapcbcEnv.getCredentialId());
            PCConnConfig config = new PCConnConfig(sapcbcEnv.getHostUrl(), cred.getUserName(), cred.getActualPassword());
            CBCTargetSystemDetails targetSystem = sapPublicCloudService.getCBCTargetDeploymentDetails(sapcbcEnv.getDeployTargetId(), sapcbcEnv.getWorkSpaceId(), config, cbcBrowserClient);
            sapcbcEnv.setCbcTargetSystemDetails(targetSystem);
        }

        sapcbcEnv = cbcEnvironmentRepo.save(sapcbcEnv);
        return sapcbcEnv;
    }

    public List<CBCEnvironment> getAllSAPCBCEnvByTenantName(String tenantName) {
        return cbcEnvironmentRepo.findAllByTenantName(tenantName);
    }

    public List<CBCWorkSpace> getCBCWorkSpace(long credId, String hostUrl) throws Exception {
        Credential credential = credentialService.getCredentialById(credId);
        if (credential == null) {
            throw new Exception("SAP CBC Credential with Id " + credId + " is not present");
        }
        PCConnConfig config = new PCConnConfig(hostUrl, credential.getUserName(), credential.getActualPassword());
        CBCBrowserClient cbcBrowserClient = new CBCBrowserClient();
        return sapPublicCloudService.getCBCWorkSpace(config, cbcBrowserClient);
    }


    public CBCEnvironment getSAPCBCEnvById(String tenantName, long id) {
        return cbcEnvironmentRepo.findByTenantNameAndId(tenantName, id).orElse(null);
    }

    public void deleteSAPCBCEnvById(String tenantName, long id) {
        cbcEnvironmentRepo.deleteByTenantNameAndId(tenantName, id);
    }

    public List<CBCArtifactSyncLog> checkSyncLogInProgress(String tenantName, long envId) {
        return cbcAtifactSyncLogRepo.findAllByTenantNameAndEnvIdAndSyncStatus(tenantName, envId, SyncStatus.IN_PROGRESS);
    }

    public CBCArtifactSyncLog initializeSyncLog(Customer userObj, long projectId, long envId) {
        CBCArtifactSyncLog syncLog = new CBCArtifactSyncLog();
        Date date = new Date();
        syncLog.setTenantName(userObj.getTenantName());
        syncLog.setEnvId(envId);
        syncLog.setProjectId(projectId);
        syncLog.setSyncStatus(SyncStatus.IN_PROGRESS);
        syncLog.setType("CBCArtifact");
        syncLog.setCreatedBy(userObj.getEmail());
        syncLog.setCreationDate(date);
        syncLog.setLastModifiedBy(userObj.getEmail());
        syncLog.setLastModifiedDate(date);
        syncLog = cbcAtifactSyncLogRepo.save(syncLog);
        return syncLog;
    }

    public void syncCBCArtifacts(Customer userObj, long projectId, long envId) throws Exception {
        String tenantName = userObj.getTenantName();
        CBCEnvironment cbcEnv = this.getSAPCBCEnvById(userObj.getTenantName(), envId);
        if (cbcEnv == null) {
            throw new Exception("SAP CBC Environment with Id " + envId + " is not present");
        }
        Credential credential = credentialService.getCredentialById(cbcEnv.getCredentialId());
        if (credential == null) {
            throw new Exception("SAP CBC Credential with Id " + cbcEnv.getCredentialId() + " is not present");
        }
        PCConnConfig config = new PCConnConfig(cbcEnv.getHostUrl(), credential.getUserName(), credential.getActualPassword());
        try {
            log.info("Fetching SAP CBC Artifacts from host {} with credId {}", cbcEnv.getHostUrl(), cbcEnv.getCredentialId());
            CBCBrowserClient cbcBrowserClient = new CBCBrowserClient();
            List<CBCArtifact> newCBCArtifacts = sapPublicCloudService.getCBCArtifacts(projectId, envId, config, cbcEnv.getCbcProjectId(), cbcBrowserClient);
            List<CBCArtifact> oldCBCArtifacts = getSAPCBCArtifactsInTenantEnv(tenantName, projectId, envId);
            SyncObjectsComparator<CBCArtifact> syncObjectsComparator = new SyncObjectsComparator<>(new CBCArtifactSyncHelper(userObj), false);
            SyncObjectsResult<CBCArtifact> syncResult = syncObjectsComparator.findDiff(oldCBCArtifacts, newCBCArtifacts);

            log.info("Saving added SAP CBC Artifacts count {}", syncResult.getAddedObjects().size());
            List<CBCArtifact> addedCBCArtifacts = cbcArtifactRepo.saveAll(syncResult.getAddedObjects());
            saveSAPCCBCArtifactVersions(addedCBCArtifacts, true);
            log.info("Saving modified SAP CBC Artifacts count {}", syncResult.getModifiedObjects().size());
            saveSAPCCBCArtifactVersions(syncResult.getModifiedObjects(), false);
            cbcArtifactRepo.saveAll(syncResult.getModifiedObjects());
            log.info("Saving deleted SAP CBC Artifacts count {}", syncResult.getDeletedObjects().size());
            for (CBCArtifact pcItem : syncResult.getDeletedObjects()) {
                pcItem.setDeleted(true);
            }
            cbcArtifactRepo.saveAll(syncResult.getDeletedObjects());
        } catch (Throwable t) {
            String msg = String.format("Failed to fetch CBC Artifacts from host %s with envId %s. Error: %s", cbcEnv.getHostUrl(), envId, t.getMessage());
            log.error(msg, t);
            throw new Exception(msg, t);
        }
    }

    public List<CBCArtifact> getSAPCBCArtifactsInTenantEnv(String tenantName, long projectId, long envId) {
        log.info("Processing request for getting all SAP CBC Artifacts for the tenant {} and env {}", tenantName, envId);
        return cbcArtifactRepo.findByTenantNameAndProjectIdAndEnvIdAndIsDeleted(tenantName, projectId, envId, false);
    }

    private void saveSAPCCBCArtifactVersions(List<CBCArtifact> addedPcArtifacts, boolean isAdded) {
        if (addedPcArtifacts != null) {
            List<CBCArtifactVersion> cbcArtifactVersions = new ArrayList<>();
            for (CBCArtifact cbcArtifact : addedPcArtifacts) {
                CBCArtifactVersion version = cbcArtifact.getCbcArtifactVersion();
                if (version != null) {
                    version.setRoArtifactId(cbcArtifact.getRoId());
                    cbcArtifactVersions.add(version);
                }
            }
            if (isAdded) {
                cbcArtifactVersionRepo.saveAll(cbcArtifactVersions);
            }
        }
    }

    public List<CBCArtifactSyncLog> getCBCArtifactsSyncHistory(Customer userObj, long envId, long projectId) {
        return cbcAtifactSyncLogRepo.findAllByTenantNameAndEnvIdAndProjectIdOrderByCreationDateDesc(userObj.getTenantName(), envId, projectId);
    }

    public List<CBCArtifactVersion> getCBCArtVersions(String tenantName, long projectId, long cbcArtifactId) {
        return cbcMysqlDAO.getCBCArtifactVersions(tenantName, projectId, cbcArtifactId);
    }

    public List<CBCArtifact> getCbcArtifactWithVersions(String tenantName, List<Long> rocbcArtVersionIds) {
        return cbcMysqlDAO.getCbcArtifactWithVersions(tenantName, rocbcArtVersionIds);
    }

    public void saveCBCArtifactSyncLog(CBCArtifactSyncLog syncLog) {
        cbcAtifactSyncLogRepo.save(syncLog);
    }

    public CBCEnvironment getCBCEnvironment(String tenantName, long id) {
        return cbcEnvironmentRepo.findByTenantNameAndId(tenantName, id).orElse(null);
    }

    public List<CBCEnvironment> getAllCBCEnvByTenantName(String tenantName) {
        return cbcEnvironmentRepo.findAllByTenantName(tenantName);
    }

    public List<CBCArtifactDeployLog> getCbcDeployLog(String wfTaskId, String tenantName) {
        return cbcArtifactDeployLogRepo.findAllByTenantNameAndWfTaskIdOrderByIdAsc(tenantName, wfTaskId);
    }

    public List<CBCArtifactDeployLog> updateCBCArtifactDeployStatusInDeployLog(Customer userObj, long projectId, String wfTaskId) throws Exception {
        List<CBCArtifactDeployLog> deployLogList = cbcArtifactDeployLogRepo.findAllByTenantNameAndProjectIdAndWfTaskIdAndDeployStatus(userObj.getTenantName(), projectId, wfTaskId, PCArtifactDeployLogStatus.TIMEOUT);
        if(ObjectUtils.isEmpty(deployLogList)) {
            return deployLogList;
        }
        CBCBrowserClient cbcBrowserClient = new CBCBrowserClient();
        Date currentDate = new Date();
        for(CBCArtifactDeployLog deployLog : deployLogList) {
            // If the artifact has already been manually completed, skip it while fetching the runtime deploy status.
            if(!ObjectUtils.isEmpty(deployLog.getDeployedType()) && "Manual".equalsIgnoreCase(deployLog.getDeployedType())) {
                continue;
            }
            CBCEnvironment cbcEnv = this.getCBCEnvironment(userObj.getTenantName(), deployLog.getCbcDeployEnvId());
            if (cbcEnv == null) {
                String error = String.format("CBC environment not found for the environment id %s", deployLog.getCbcDeployEnvId());
                log.error(error);
                throw new ROPublicCloudException(error);
            }
            Credential cred = this.credentialService.getCredentialById(cbcEnv.getCredentialId());
            if (cred == null) {
                String error = String.format("Credentials not found for the CBC environment '%s'.", cbcEnv.getName());
                log.error(error);
                throw new ROPublicCloudException(error);
            }
            PCConnConfig config = new PCConnConfig(cbcEnv.getHostUrl(), cred.getUserName(), cred.getActualPassword());
            RuntimeImportArtifact runtimeArt = sapPublicCloudService.getCbcDeployProgress(cbcEnv.getWorkSpaceId(), config, cbcBrowserClient);
            deployLog.setCbcDeployPercentage(runtimeArt.getCbcDeploymentPercentage());
            deployLog.setCbcDeployProcessTime(runtimeArt.getCbcDeployProcessTime());
            deployLog.setTenantName(userObj.getTenantName());
            deployLog.setLastModifiedBy(userObj.getUsername());
            deployLog.setLastModifiedDate(currentDate);
            if(runtimeArt.getCbcDeploymentPercentage() == 0) {
                deployLog.setDeployStatus(PCArtifactDeployLogStatus.SUCCESS);
            }
            List<JsonNode> deployTargetHistoryDetails = sapPublicCloudService.deploymentHistoryDetailsForCBCArtifact(cbcEnv.getDeployTargetId(), config, cbcBrowserClient);
/*            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            formatter.setTimeZone(TimeZone.getTimeZone("UTC"));*/
            for (JsonNode deployHistory : deployTargetHistoryDetails) {
//                Date deployDate = formatter.parse(deployHistory.get("Date").asText());
                Date deployDate = DateUtil.convertCBCStringToDate(deployHistory.get("Date").asText());
                if (currentDate.before(deployDate)) {
                    deployLog.setDeploymentTargetDetails(deployHistory);
                    deployLog.setExternalStagingId(deployHistory.get("ExternalStagingId").asText());
                }
            }
            cbcArtifactDeployLogRepo.save(deployLog);
        }
        return deployLogList;
    }

    public CBCArtifactDeployLog updateCBCDeployLogManual(CBCArtifactDeployLog cbcDeployLog, long projectId, Customer userObj) {
        try {
            this.getCbcChangeSetLogs(userObj, projectId, cbcDeployLog.getRoCBCArtifactId(), cbcDeployLog.getRoCbcArtifactVersionId());
//        this.writeCpiManualDeployedCommentInAlm(cpiDeployLog, userObj, currentDate);
        } catch (Throwable t) {
            log.error(ExceptionUtils.getRootCauseMessage(t), t);
        }
        Date currentDate = new Date();
        cbcDeployLog.setDeployedType("Manual");
        cbcDeployLog.setManualDeployDate(currentDate);
        cbcDeployLog.setManualDeployedBy(userObj.getUsername());
        cbcDeployLog.setLastModifiedDate(currentDate);
        cbcDeployLog.setLastModifiedBy(userObj.getUsername());
        cbcArtifactDeployLogRepo.updateDeployLogManual(cbcDeployLog.getId(), cbcDeployLog.getDeployedType(), cbcDeployLog.getManualDeployedBy(), cbcDeployLog.getManualDeployedReason(), cbcDeployLog.getManualDeployDate(), cbcDeployLog.getLastModifiedDate(), cbcDeployLog.getLastModifiedBy());
        cbcArtifactVersionRepo.updateStatus("TASK_COMPLETED", cbcDeployLog.getRoCbcArtifactVersionId(), userObj.getTenantName());
        return cbcDeployLog;
    }

    public JsonNode getCbcChangeSetLogs(Customer userObj, long projectId, long roArtifactId, long roVersionId) throws ROPublicCloudException, URISyntaxException, JsonProcessingException {
        CBCArtifactVersion cbcArtVer = cbcArtifactVersionRepo.findByTenantNameAndProjectIdAndRoArtifactIdAndRoVersionId(userObj.getTenantName(), projectId, roArtifactId, roVersionId);
        CBCChangeSetLogs cbcLogs = cbcChangeSetLogsRepo.findByTenantNameAndProjectIdAndRoArtifactIdAndRoVersionId(userObj.getTenantName(), projectId, roArtifactId, roVersionId);
        if ((cbcLogs !=null && !ObjectUtils.isEmpty(cbcLogs.getCbcChangeSetLogs())) && "TASK_COMPLETED".equalsIgnoreCase(cbcArtVer.getStatus()) ) {
            JsonNode logsNode = cbcLogs.getCbcChangeSetLogs().get("data").get(0).get("data").get("logs");
            if(logsNode.isArray() && !logsNode.isEmpty()) {
                return cbcLogs.getCbcChangeSetLogs();
            }
        }
        CBCArtifactDeployLog currentDeployLog = cbcArtifactDeployLogRepo.findTopByTenantNameAndProjectIdAndRoCbcArtifactVersionIdOrderByIdDesc(userObj.getTenantName(), projectId, roVersionId);
        Date endDate = null;
        Date startDate = null;
        if (currentDeployLog == null) {
            endDate = new Date();
        } else {
            endDate = currentDeployLog.getSapCbcDeploymentDate();
            if (endDate == null) {
                endDate = currentDeployLog.getLastModifiedDate();
            }
        }
        CBCArtifactDeployLog previousDeployLog = cbcArtifactDeployLogRepo.findTopByTenantNameAndProjectIdAndRoCbcArtifactVersionIdOrderByIdDesc(userObj.getTenantName(), projectId, roVersionId - 1);
        if (previousDeployLog == null) {
            startDate = cbcArtVer.getLastModifiedDate();
        } else {
            startDate = previousDeployLog.getSapCbcDeploymentDate();
            if (startDate == null) {
                startDate = previousDeployLog.getLastModifiedDate();
            } else {
                // If the previous version is already deployed, add one second to the timestamp to retrieve the changeset logs  that occurred after the deployment.
                startDate = new Date(startDate.getTime() + 100);
            }
        }

        CBCEnvironment cbcEnv = this.getCBCEnvironment(userObj.getTenantName(), cbcArtVer.getCbcEnvId());
        if (cbcEnv == null) {
            String error = String.format("CBC environment not found for the environment id %s", cbcArtVer.getCbcEnvId());
            log.error(error);
            throw new ROPublicCloudException(error);
        }
        Credential cred = this.credentialService.getCredentialById(cbcEnv.getCredentialId());
        if (cred == null) {
            String error = String.format("Credentials not found for the CBC environment '%s'.", cbcEnv.getName());
            log.error(error);
            throw new ROPublicCloudException(error);
        }
        PCConnConfig config = new PCConnConfig(cbcEnv.getHostUrl(), cred.getUserName(), cred.getActualPassword());
        CBCBrowserClient cbcBrowserClient = new CBCBrowserClient();
        JsonNode changeLogNode = sapPublicCloudService.getCbcChangeSetLog(startDate, endDate, cbcEnv.getWorkSpaceId(), cbcEnv.getCbcProjectId(), config, cbcBrowserClient);
        Date currentDate = new Date();
        if (cbcLogs == null) {
            cbcLogs = new CBCChangeSetLogs();
            cbcLogs.setRoArtifactId(roArtifactId);
            cbcLogs.setRoVersionId(roVersionId);
            cbcLogs.setProjectId(projectId);
            cbcLogs.setCbcEnvId(cbcArtVer.getCbcEnvId());
            cbcLogs.setCreatedBy(userObj.getUsername());
            cbcLogs.setCreationDate(currentDate);
        } else {
            cbcLogs.setCreatedBy(cbcLogs.getCreatedBy());
            cbcLogs.setCreationDate(cbcLogs.getCreationDate());
        }
        cbcLogs.setTenantName(userObj.getTenantName());
        cbcLogs.setLastModifiedBy(userObj.getUsername());
        cbcLogs.setLastModifiedDate(currentDate);
        cbcLogs.setCbcChangeSetLogs(changeLogNode);
        cbcChangeSetLogsRepo.save(cbcLogs);
        return changeLogNode;

    }
}
