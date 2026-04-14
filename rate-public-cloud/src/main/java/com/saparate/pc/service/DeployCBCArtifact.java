package com.saparate.pc.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.rate.commons.DateUtil;
import com.rate.commons.ROCallableTask;
import com.rate.commons.ROExecutorService;
import com.rate.commons.enums.ReleaseForType;
import com.rate.user.entity.Credential;
import com.rate.user.entity.Customer;
import com.rate.user.services.CredentialService;
import com.saparate.pc.client.CBCBrowserClient;
import com.saparate.pc.config.PCConnConfig;
import com.saparate.pc.entity.CBCArtifact;
import com.saparate.pc.entity.CBCArtifactDeployLog;
import com.saparate.pc.entity.CBCArtifactVersion;
import com.saparate.pc.entity.CBCEnvironment;
import com.saparate.pc.enums.PCArtifactDeployLogStatus;
import com.saparate.pc.model.RuntimeImportArtifact;
import com.saparate.pc.repository.CBCArtifactDeployLogRepo;
import com.saparate.pc.repository.CBCArtifactVersionRepo;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.MDC;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import static com.saparate.pc.enums.PCArtifactDeployLogStatus.SUCCESS;
import static com.saparate.pc.enums.PCArtifactDeployLogStatus.TIMEOUT;

@Slf4j
@Getter
@Builder
public class DeployCBCArtifact {
    private ROCBCService roCBCService;
    private SAPPublicCloudService sapPCService;
    private CBCArtifactDeployLogRepo cbcArtifactDeployRepo;
    private List<CBCArtifact> cbcArtifactList;
    private CredentialService credentialService;
    private String contextStatus;
    private long projectId;
    private long releaseForId;
    private ReleaseForType releaseForType;
    private String correlationId;
    private String tenantName;
    private String initiatedBy;
    private Map<String, String> contextMap;
    private ROExecutorService executorService;
    private CBCArtifactVersionRepo cbcArtifactVersionRepo;

    public Map<Long, CBCArtifactDeployLog> processCbcArtifactDeploy() {
        List<CBCArtifact> cbcArtifactList = new ArrayList<>(this.cbcArtifactList);
        // Skip Already deployed and manual completion CBC artifact During Retry or Continue.
        this.getPreviousCbcArtDeployLog(cbcArtifactList);
        Map<Long, CBCArtifactDeployLog> cbcArtifactDeployLogMap = new HashMap<>();

        for (CBCArtifact cbcArt : cbcArtifactList) {
            Date currentDate = new Date();
            CBCArtifactDeployLog cbcArtifactDeployLog = cbcArtifactDeployLogMap.get(cbcArt.getCbcArtifactVersion().getRoVersionId());
            if (cbcArtifactDeployLog == null) {
                cbcArtifactDeployLog = prepareCbcDeployLog(currentDate, cbcArt);
            }
            cbcArtifactDeployLog.setDeployStatus(PCArtifactDeployLogStatus.NOT_STARTED);
            cbcArtifactDeployLog = this.cbcArtifactDeployRepo.save(cbcArtifactDeployLog);
            cbcArtifactDeployLogMap.put(cbcArt.getCbcArtifactVersion().getRoVersionId(), cbcArtifactDeployLog);
        }

        deployCBCArtifact(cbcArtifactList, cbcArtifactDeployLogMap);
        return cbcArtifactDeployLogMap;
    }


    private void deployCBCArtifact(List<CBCArtifact> cbcArtifactsList, Map<Long, CBCArtifactDeployLog> cbcArtifactDeployLogMap) {
        CBCBrowserClient client = new CBCBrowserClient();
        for (CBCArtifact cbcArtifact : cbcArtifactsList) {
            Date currentDate = new Date();
            CBCArtifactDeployLog cbcArtDeployLog = cbcArtifactDeployLogMap.get(cbcArtifact.getCbcArtifactVersion().getRoVersionId());
            if (cbcArtDeployLog == null) {
                cbcArtDeployLog = prepareCbcDeployLog(currentDate, cbcArtifact);
            }
            cbcArtDeployLog.setDeployStatus(PCArtifactDeployLogStatus.IN_PROGRESS);
            cbcArtDeployLog = this.cbcArtifactDeployRepo.save(cbcArtDeployLog);
            cbcArtifactDeployLogMap.put(cbcArtifact.getCbcArtifactVersion().getRoVersionId(), cbcArtDeployLog);
            if (!("TASK_REOPENED".equalsIgnoreCase(cbcArtifact.getCbcArtifactVersion().getStatus()))) {
                cbcArtDeployLog.setDeployStatus(PCArtifactDeployLogStatus.FAILED);
                cbcArtDeployLog.setDeployErrorMsg("Then CBC Artifact Status is not Open.");
                this.cbcArtifactDeployRepo.save(cbcArtDeployLog);
                continue;
            }
            CBCEnvironment cbcEnv = this.roCBCService.getCBCEnvironment(this.tenantName, cbcArtifact.getEnvId());
            if (cbcEnv == null) {
                String error = String.format("CBC environment not found for the environment id %s", cbcArtifact.getEnvId());
                log.error(error);
                cbcArtDeployLog.setDeployStatus(PCArtifactDeployLogStatus.FAILED);
                cbcArtDeployLog.setDeployErrorMsg(error);
                this.cbcArtifactDeployRepo.save(cbcArtDeployLog);
                continue;
            }
            Credential cred = this.roCBCService.getCredentialService().getCredentialById(cbcEnv.getCredentialId());
            if (cred == null) {
                String error = String.format("Credentials not found for the CBC environment '%s'.", cbcEnv.getName());
                log.error(error);
                cbcArtDeployLog.setDeployStatus(PCArtifactDeployLogStatus.FAILED);
                cbcArtDeployLog.setDeployErrorMsg(error);
                this.cbcArtifactDeployRepo.save(cbcArtDeployLog);
                continue;
            }
            PCConnConfig config = new PCConnConfig(cbcEnv.getHostUrl(), cred.getUserName(), cred.getActualPassword());
            try {
                boolean isAllowedDeploy = sapPCService.isAllowedDeployCBArtifact(cbcEnv.getWorkSpaceId(), config, client);
                if (isAllowedDeploy) {
                    String stringResponse = sapPCService.deployCBCArtifact(cbcEnv.getWorkSpaceId(), cbcArtifact.getCbcArtifactVersion().getUsSummary(), config, client);
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode pNode = mapper.readTree(stringResponse);
                    if (pNode.has("success")) {
                        if (!(pNode.get("success").asBoolean())) {
                            cbcArtDeployLog.setDeployStatus(PCArtifactDeployLogStatus.FAILED);
                            cbcArtDeployLog.setDeployErrorMsg(stringResponse);
                        }
                    } else {
                        JsonNode data = pNode.get("data").get(0);
                        if (data != null) {
                            if (data.has("success")) {
                                if (!(data.get("success").asBoolean())) {
                                    cbcArtDeployLog.setDeployStatus(PCArtifactDeployLogStatus.FAILED);
                                    cbcArtDeployLog.setDeployErrorMsg(stringResponse);
                                }
                            }
                        } else {
                            cbcArtDeployLog.setDeployStatus(PCArtifactDeployLogStatus.FAILED);
                            cbcArtDeployLog.setDeployErrorMsg(stringResponse);
                        }
                    }
                } else {
                    cbcArtDeployLog.setDeployStatus(PCArtifactDeployLogStatus.FAILED);
                    cbcArtDeployLog.setDeployErrorMsg("Deployment of the CBC Artifact is not permitted.");
                }
                // check runtime cbc artifact deploy
                if (PCArtifactDeployLogStatus.FAILED != cbcArtDeployLog.getDeployStatus()) {
                    Future<RuntimeImportArtifact> pcImportArtifact = this.executorService.submitCallable(new CBCDeployStatusPoller(this.tenantName, cbcArtifact.getRoId(), cbcEnv.getWorkSpaceId(), this.sapPCService, config, client), "runtimeCBCDeployPercentage");
                    RuntimeImportArtifact runtimeArt = pcImportArtifact.get();
                    if (runtimeArt != null) {
                        cbcArtDeployLog.setCbcDeployPercentage(runtimeArt.getCbcDeploymentPercentage());
                        cbcArtDeployLog.setCbcDeployProcessTime(runtimeArt.getCbcDeployProcessTime());
                        if (runtimeArt.getCbcDeploymentPercentage() == 0) {
                            cbcArtDeployLog.setDeployStatus(SUCCESS);
                        } else {
                            cbcArtDeployLog.setDeployStatus(TIMEOUT);
                            cbcArtDeployLog.setDeployErrorMsg(String.format("The Import has been triggered, but the deployment percentage is '%s'. Check the status manually in the CBC environment.", runtimeArt.getCbcDeploymentPercentage()));
                        }
                    } else {
                        cbcArtDeployLog.setDeployStatus(PCArtifactDeployLogStatus.FAILED);
                        cbcArtDeployLog.setDeployErrorMsg("failed get runtime CBC artifact percentage.");
                    }
                }
                if (SUCCESS == cbcArtDeployLog.getDeployStatus()) {
                    Customer userObj =  Customer.createUserObj(this.initiatedBy, this.tenantName);
                    try {
                        this.roCBCService.getCbcChangeSetLogs(userObj, this.projectId, cbcArtDeployLog.getRoCBCArtifactId(), cbcArtDeployLog.getRoCbcArtifactVersionId());
                    } catch (Throwable t) {
                        log.error("Exception while getting changeset logs for the version '{}' with error msg {}", cbcArtDeployLog.getVersion(), ExceptionUtils.getRootCauseMessage(t), t);
                    }
                    cbcArtifactVersionRepo.updateStatus("TASK_COMPLETED", cbcArtDeployLog.getRoCbcArtifactVersionId(), cbcArtDeployLog.getTenantName());
                }
                for (int i=0;i<6;i++) {
                    Thread.sleep(10000L);
                    List<JsonNode> deployTargetHistoryDetails = sapPCService.deploymentHistoryDetailsForCBCArtifact(cbcEnv.getDeployTargetId(), config, client);
                  /*  SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                    formatter.setTimeZone(TimeZone.getTimeZone("UTC"));*/
                    for (JsonNode deployHistory : deployTargetHistoryDetails) {
//                        Date deployDate = formatter.parse(deployHistory.get("Date").asText());
                        Date deployDate = DateUtil.convertCBCStringToDate(deployHistory.get("Date").asText());
                        if (currentDate.before(deployDate)) {
                            cbcArtDeployLog.setDeploymentTargetDetails(deployHistory);
                            cbcArtDeployLog.setExternalStagingId(deployHistory.get("ExternalStagingId").asText());
                            cbcArtDeployLog.setSapCbcDeploymentDate(deployDate);
                            if ( cbcEnv.getCbcTargetSystemDetails() != null ) {
                                cbcArtDeployLog.setCbcTenantUrl(cbcEnv.getCbcTargetSystemDetails().getCbcTenantUrl());
                            }
                            break;
                        }
                    }
                    if (!ObjectUtils.isEmpty(cbcArtDeployLog.getExternalStagingId()) ) {

                        break;
                    }
                }
                this.cbcArtifactDeployRepo.save(cbcArtDeployLog);
            } catch (RestClientResponseException e) {
                cbcArtDeployLog.setDeployStatus(PCArtifactDeployLogStatus.FAILED);
                cbcArtDeployLog.setDeployErrorMsg(e.getResponseBodyAsString() + " --- \r\n --- " + e);
                this.cbcArtifactDeployRepo.save(cbcArtDeployLog);
                log.error(e.getResponseBodyAsString(), e);
            } catch (Throwable e) {
                cbcArtDeployLog.setDeployStatus(PCArtifactDeployLogStatus.FAILED);
                cbcArtDeployLog.setDeployErrorMsg(e.getMessage() + "--- \n ---" + ExceptionUtils.getStackTrace(e));
                log.error("Failed during import or initialing polling of import.", e);
                this.cbcArtifactDeployRepo.save(cbcArtDeployLog);
            }
        }
    }

    private void getPreviousCbcArtDeployLog(List<CBCArtifact> deployCbcArtifactsList) {
        Date currentData = new Date();
        for (CBCArtifact cbcArtifact : this.cbcArtifactList) {
            CBCArtifactDeployLog previousLog = cbcArtifactDeployRepo.findTopByTenantNameAndProjectIdAndCbcDeployEnvIdAndRoCbcArtifactVersionIdOrderByIdDesc(tenantName, projectId, cbcArtifact.getEnvId(), cbcArtifact.getCbcArtifactVersion().getRoVersionId());
            if (previousLog != null) {
                //TODO: If an artifact is re-assigned to another user story, we need to update the previous deploy log with the details of the new user story.
                if (this.releaseForId != previousLog.getReleaseForId() || this.releaseForType != previousLog.getReleaseForType()) {
                    CBCArtifactVersion version = cbcArtifact.getCbcArtifactVersion();
                    previousLog.setProjUsId(version.getProjUserStoryId());
                    previousLog.setUserStoryId(version.getUserStoryId());
                    previousLog.setExtUsId(version.getExtUsId());
                    previousLog.setReleaseForId(this.releaseForId);
                    previousLog.setReleaseForType(this.releaseForType);
                }
                if (SUCCESS == previousLog.getDeployStatus() || (previousLog.getDeployedType() != null && "Manual".equalsIgnoreCase(previousLog.getDeployedType()))) {
                    if (!this.correlationId.equals(previousLog.getWfTaskId())) {
                        CBCArtifactDeployLog deployLog = this.preparePreviousCbcArtDeployLog(previousLog, currentData);
                        deployLog.setDeployed(true);
                        cbcArtifactDeployRepo.save(deployLog);
                    }
//                    cbcArtifactVersionRepo.updateStatus("TASK_COMPLETED", previousLog.getRoCbcArtifactVersionId(), this.tenantName);
                    deployCbcArtifactsList.remove(cbcArtifact);
                }
            }
        }
    }

    private CBCArtifactDeployLog prepareCbcDeployLog(Date currentDate, CBCArtifact cbcArtifact) {

        CBCArtifactDeployLog cbcArDeployLog = new CBCArtifactDeployLog();
        cbcArDeployLog.setRoCBCArtifactId(cbcArtifact.getRoId());
        cbcArDeployLog.setRoCbcArtifactVersionId(cbcArtifact.getCbcArtifactVersion().getRoVersionId());
        cbcArDeployLog.setCbcArtifactName(cbcArtifact.getName());
        cbcArDeployLog.setVersion(cbcArtifact.getCbcArtifactVersion().getVersion());
        cbcArDeployLog.setCreatedBy(this.initiatedBy);
        cbcArDeployLog.setCreationDate(currentDate);
        cbcArDeployLog.setLastModifiedBy(this.initiatedBy);
        cbcArDeployLog.setLastModifiedDate(currentDate);
        cbcArDeployLog.setTenantName(this.tenantName);
        cbcArDeployLog.setWfTaskId(this.correlationId);
        cbcArDeployLog.setReleaseForId(this.releaseForId);
        cbcArDeployLog.setReleaseForType(this.releaseForType);
        cbcArDeployLog.setUserStoryId(cbcArtifact.getCbcArtifactVersion().getUserStoryId());
        cbcArDeployLog.setProjUsId(cbcArtifact.getCbcArtifactVersion().getProjUserStoryId());
        cbcArDeployLog.setExtUsId(cbcArtifact.getCbcArtifactVersion().getExtUsId());
        cbcArDeployLog.setCbcDeployEnvId(cbcArtifact.getEnvId());
        cbcArDeployLog.setProjectId(this.projectId);
        cbcArDeployLog.setCbcContextMap(this.contextMap);
        return cbcArDeployLog;
    }

    private CBCArtifactDeployLog preparePreviousCbcArtDeployLog(CBCArtifactDeployLog previousDeployLog, Date currentDate) {
        try {
            CBCArtifactDeployLog deployLog = previousDeployLog.clone();
            deployLog.setId(0L);
            deployLog.setWfTaskId(this.correlationId);
            deployLog.setCbcContextMap(this.contextMap);
            deployLog.setCreatedBy(this.initiatedBy);
            deployLog.setCreationDate(currentDate);
            deployLog.setLastModifiedBy(this.initiatedBy);
            deployLog.setLastModifiedDate(currentDate);
            return deployLog;
        } catch (CloneNotSupportedException e) {
            log.error("Exception while cloning CBC artifact deployLog with id {}", previousDeployLog.getId());
            throw new RuntimeException(e);
        }
    }


    public class CBCDeployStatusPoller implements ROCallableTask<RuntimeImportArtifact> {

        private String tenantName;
        private long roArtifactId;
        private String workSpaceId;
        private SAPPublicCloudService sapPcService;
        private PCConnConfig cbcConnConfig;
        CBCBrowserClient cbcBrowserClient;
        private int iterations = 6;

        public CBCDeployStatusPoller(String tenantName, long roArtifactId, String workSpaceId, SAPPublicCloudService sapPcService, PCConnConfig cbcConnConfig, CBCBrowserClient cbcBrowserClient) {
            this.tenantName = tenantName;
            this.roArtifactId = roArtifactId;
            this.workSpaceId = workSpaceId;
            this.sapPcService = sapPcService;
            this.cbcConnConfig = cbcConnConfig;
            this.cbcBrowserClient = cbcBrowserClient;
        }

        @Override
        public String getThreadName() {
            return String.format("CBC-Deploy-Status-poll-%s", this.roArtifactId);
        }

        @Override
        public void setThreadContext() {
            MDC.put("tenant", tenantName);

        }

        @Override
        public RuntimeImportArtifact call() throws Exception {
            RuntimeImportArtifact runtimeImportArtifact = null;
            for (int i = 0; i < this.iterations; i++) {
                try {
                    Thread.sleep(10000);
                    runtimeImportArtifact = sapPcService.getCbcDeployProgress(workSpaceId, cbcConnConfig, cbcBrowserClient);
                    if (runtimeImportArtifact != null) {
                        if (runtimeImportArtifact.getCbcDeploymentPercentage() == 0) {
                            break;
                        } else {
                            Thread.sleep(10000);
                        }
                    }
                } catch (Throwable e) {
                    throw e;
                }
            }
            return runtimeImportArtifact;
        }

    }

}
