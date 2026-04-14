package com.saparate.pc.service;

import tools.jackson.databind.JsonNode;
import com.rate.commons.ROCallableTask;
import com.rate.commons.ROExecutorService;
import com.rate.commons.enums.ReleaseForType;
import com.rate.user.entity.Credential;
import com.rate.user.services.CredentialService;
import com.saparate.pc.client.PCBrowserClient;
import com.saparate.pc.config.PCConnConfig;
import com.saparate.pc.entity.PCArtifactDeployLog;
import com.saparate.pc.entity.PCArtifact;
import com.saparate.pc.entity.PCArtifactVersion;
import com.saparate.pc.entity.PCEnvironment;
import com.saparate.pc.enums.PCArtifactDeployLogStatus;
import com.saparate.pc.exception.ROPublicCloudException;
import com.saparate.pc.model.RoODataBatchResponse;
import com.saparate.pc.model.RuntimeImportArtifact;
import com.saparate.pc.repository.PCArtifactDeployLogRepo;
import com.saparate.pc.repository.PCArtifactRepo;
import com.saparate.pc.repository.PCArtifactVersionRepo;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;


@Slf4j
@Builder
public class DeployPCArtifact {
    private ROPublicCloudService roPcArtifactService;
    private SAPPublicCloudService sapPCService;
    private PCArtifactDeployLogRepo pcArtifactDeployLogRepo;
    private PCArtifactRepo pcArtifactRepo;
    private PCArtifactVersionRepo pcArtifactVersionRepo;
    private PCEnvironment targetPcEnv;
    private List<PCArtifact> pcArtifactList;
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

    public Map<Long, PCArtifactDeployLog> processPcArtifactDeploy() throws ROPublicCloudException {
        Credential cred = roPcArtifactService.getCredentialService().getCredentialById(targetPcEnv.getCredentialId());
        if (cred == null) {
            String error = String.format("Credentials not found for target public cloud environment '%s'.", targetPcEnv.getName());
            log.error(error);
            throw new ROPublicCloudException(error);
        }
        PCConnConfig pcConnConfig = new PCConnConfig(targetPcEnv.getHostUrl(), cred.getUserName(), cred.getActualPassword());
        List<PCArtifact> pcArtifactList = new ArrayList<>(this.pcArtifactList);
        // Skip Already deployed and manual completion public cloud artifact During Retry or Continue.
        this.getPreviousPcArtDeployLog(pcArtifactList);

        Map<Long, PCArtifactDeployLog> pcArtifactDeployLogMap = new HashMap<>();

        for (PCArtifact pcArt : pcArtifactList) {
            Date currentDate = new Date();
            PCArtifactDeployLog pcArtifactDeployLog = pcArtifactDeployLogMap.get(pcArt.getPcArtifactVersion().getRoVersionId());
            if (pcArtifactDeployLog == null) {
                pcArtifactDeployLog = preparePcDeployLog(currentDate, pcArt);
            }
            pcArtifactDeployLog.setDeployStatus(PCArtifactDeployLogStatus.NOT_STARTED);
            pcArtifactDeployLog = this.pcArtifactDeployLogRepo.save(pcArtifactDeployLog);
            pcArtifactDeployLogMap.put(pcArt.getPcArtifactVersion().getRoVersionId(), pcArtifactDeployLog);
        }

        if(!ObjectUtils.isEmpty(pcArtifactList)) {
            deployPublicCloudArtifact(pcArtifactList, pcConnConfig, pcArtifactDeployLogMap);
        }
        return pcArtifactDeployLogMap;
    }

    private void deployPublicCloudArtifact(List<PCArtifact> pcArtifactsList, PCConnConfig pcConnConfig, Map<Long, PCArtifactDeployLog> pcArtifactDeployLogMap) {
        try {
            PCBrowserClient pcBrowserClient = new PCBrowserClient();
            Future<Map<Long, PCArtifactDeployLog>>  beforeImportFutureMap =  this.executorService.submitCallable(new PCImportStatusPoller(pcArtifactsList, pcArtifactDeployLogMap, this.pcArtifactDeployLogRepo, false, this.targetPcEnv.getClientId(), this.tenantName, sapPCService, pcConnConfig, pcBrowserClient ), "importTask");
            pcArtifactDeployLogMap = beforeImportFutureMap.get();
            List<PCArtifact> pcArtList = new ArrayList<>(pcArtifactsList);
            for(PCArtifact art : pcArtifactsList) {
                PCArtifactDeployLog log = pcArtifactDeployLogMap.get(art.getPcArtifactVersion().getRoVersionId());
                switch (log.getDeployStatus()) {
                    case SUCCESS :
                    case FAILED : {
                        pcArtList.remove(art);
                        break;
                    }
                    case NOT_STARTED: {
                        log.setDeployErrorMsg(String.format("The Collection '%s' with version '%s' is not found the the target environment '%s'", log.getSapPcArtifactId(), log.getVersion(), this.targetPcEnv.getName()));
                        log.setDeployStatus(PCArtifactDeployLogStatus.FAILED);
                        this.pcArtifactDeployLogRepo.save(log);
                        pcArtList.remove(art);
                    }
                }
            }

            // import Transport
            if(!ObjectUtils.isEmpty(pcArtList)) {
                List<RoODataBatchResponse> oDataRes = sapPCService.importAllPcArtifact(pcArtList, targetPcEnv.getClientId(), pcConnConfig, pcBrowserClient);
                int statusCode = oDataRes.getFirst().getStatusCode();
                boolean isImportSuccess = true;
                String deployErrMsg = null;
                if(!(statusCode >= 200 && statusCode <= 299)) {
                    isImportSuccess = false;
                    JsonNode errorNode = oDataRes.getFirst().getJsonBody();
                    if(!ObjectUtils.isEmpty(errorNode) && errorNode.has("error")) {
                        JsonNode errorDetailsNode = errorNode.get("error").get("message");
                        if(!ObjectUtils.isEmpty(errorDetailsNode) && errorDetailsNode.has("value")) {
                            deployErrMsg = errorDetailsNode.get("value").asText();
                        }
                    }
                }
                if(isImportSuccess) {
                    Future<Map<Long, PCArtifactDeployLog>> afterImportFutureMap = this.executorService.submitCallable(new PCImportStatusPoller(pcArtList, pcArtifactDeployLogMap, this.pcArtifactDeployLogRepo, true, this.targetPcEnv.getClientId(), this.tenantName, sapPCService, pcConnConfig, pcBrowserClient), "importTask");
                    pcArtifactDeployLogMap = afterImportFutureMap.get();
                    for (PCArtifactDeployLog log : pcArtifactDeployLogMap.values()) {
                        if (PCArtifactDeployLogStatus.TIMEOUT == log.getDeployStatus()) {
                            this.pcArtifactDeployLogRepo.save(log);
                        } else if (PCArtifactDeployLogStatus.SUCCESS == log.getDeployStatus()) {
                            log.setDeployErrorMsg("");
                            this.pcArtifactDeployLogRepo.save(log);
                        }
                    }
                } else {
                    for(PCArtifact art : pcArtList) {
                        PCArtifactDeployLog log = pcArtifactDeployLogMap.get(art.getPcArtifactVersion().getRoVersionId());
                        deployErrMsg = ObjectUtils.isEmpty(deployErrMsg) ? "Import has been failed please check manually." :deployErrMsg;
                        log.setDeployStatus(PCArtifactDeployLogStatus.FAILED);
                        log.setDeployErrorMsg(deployErrMsg);
                        this.pcArtifactDeployLogRepo.save(log);
                    }
                }
            }
        } catch (Throwable t) {
            log.error("Exception while import public cloud artifact with error msg {}", ExceptionUtils.getRootCauseMessage(t),t);
        }
    }

    private void getPreviousPcArtDeployLog(List<PCArtifact> deployPcArtifactsList) {
        Date currentData = new Date();
        for (PCArtifact pcArtifact : this.pcArtifactList) {
            PCArtifactDeployLog previousLog = pcArtifactDeployLogRepo.findTopByTenantNameAndProjectIdAndPcDeployEnvIdAndRoPcArtifactVersionIdOrderByIdDesc(tenantName, projectId, targetPcEnv.getId(), pcArtifact.getPcArtifactVersion().getRoVersionId());
            if (previousLog != null) {
                //TODO: If an artifact is re-assigned to another user story, we need to update the previous deploy log with the details of the new user story.
                if (this.releaseForId != previousLog.getReleaseForId() || this.releaseForType != previousLog.getReleaseForType()) {
                    PCArtifactVersion version = pcArtifact.getPcArtifactVersion();
                    previousLog.setProjUsId(version.getProjUserStoryId());
                    previousLog.setUserStoryId(version.getUserStoryId());
                    previousLog.setExtUsId(version.getExtUsId());
                    previousLog.setReleaseForId(this.releaseForId);
                    previousLog.setReleaseForType(this.releaseForType);
                }
                if (PCArtifactDeployLogStatus.SUCCESS == previousLog.getDeployStatus() || (previousLog.getDeployedType() != null && "Manual".equalsIgnoreCase(previousLog.getDeployedType()))) {
                    if (!this.correlationId.equals(previousLog.getWfTaskId())) {
                        PCArtifactDeployLog deployLog = this.preparePreviousPcArtDeployLog(previousLog, currentData);
                        deployLog.setDeployed(true);
                        pcArtifactDeployLogRepo.save(deployLog);
                    }
                    deployPcArtifactsList.remove(pcArtifact);
                }
            }
        }
    }

    private PCArtifactDeployLog preparePcDeployLog(Date currentDate, PCArtifact pcArtifact) {

        PCArtifactDeployLog pcArDeployLog = new PCArtifactDeployLog();
        pcArDeployLog.setRoPcArtifactId(pcArtifact.getRoId());
        pcArDeployLog.setRoPcArtifactVersionId(pcArtifact.getPcArtifactVersion().getRoVersionId());
        pcArDeployLog.setPcArtifactName(pcArtifact.getName());
        pcArDeployLog.setSapPcArtifactId(pcArtifact.getArtifactId());
        pcArDeployLog.setVersion(pcArtifact.getPcArtifactVersion().getVersion());
        pcArDeployLog.setCreatedBy(this.initiatedBy);
        pcArDeployLog.setCreationDate(currentDate);
        pcArDeployLog.setLastModifiedBy(this.initiatedBy);
        pcArDeployLog.setLastModifiedDate(currentDate);
        pcArDeployLog.setTenantName(this.tenantName);
        pcArDeployLog.setWfTaskId(this.correlationId);
        pcArDeployLog.setReleaseForId(this.releaseForId);
        pcArDeployLog.setReleaseForType(this.releaseForType);
        pcArDeployLog.setUserStoryId(pcArtifact.getPcArtifactVersion().getUserStoryId());
        pcArDeployLog.setProjUsId(pcArtifact.getPcArtifactVersion().getProjUserStoryId());
        pcArDeployLog.setExtUsId(pcArtifact.getPcArtifactVersion().getExtUsId());
        pcArDeployLog.setPcDeployEnvId(this.targetPcEnv.getId());
        pcArDeployLog.setProjectId(this.projectId);
        pcArDeployLog.setPcArtifactContextMap(this.contextMap);
        pcArDeployLog.setArtifactType(pcArtifact.getType());
        return pcArDeployLog;
    }

    private PCArtifactDeployLog preparePreviousPcArtDeployLog(PCArtifactDeployLog previousDeployLog, Date currentDate) {
        try {
            PCArtifactDeployLog pcDeployLog = previousDeployLog.clone();
            pcDeployLog.setId(0L);
            pcDeployLog.setWfTaskId(this.correlationId);
            pcDeployLog.setPcArtifactContextMap(this.contextMap);
            pcDeployLog.setCreatedBy(this.initiatedBy);
            pcDeployLog.setCreationDate(currentDate);
            pcDeployLog.setLastModifiedBy(this.initiatedBy);
            pcDeployLog.setLastModifiedDate(currentDate);
            return pcDeployLog;
        } catch (CloneNotSupportedException e) {
            log.error("Exception while cloning public cloud artifact deployLog with id {}", previousDeployLog.getId());
            throw new RuntimeException(e);
        }
    }

    public class PCImportStatusPoller implements ROCallableTask<Map<Long, PCArtifactDeployLog>> {

        private List<PCArtifact> pcArtifactList;
        private Map<Long, PCArtifactDeployLog> pcArtifactDeployLogMap;
        private PCArtifactDeployLogRepo deployLogRepo;
        private boolean isImport;
        private String tenantName;
        private String clientId;
        private SAPPublicCloudService sapPcService;
        private PCConnConfig pcConnConfig;
        PCBrowserClient pcBrowserClient;
        private int iterations = 6;

        public PCImportStatusPoller(List<PCArtifact> pcArtifactList, Map<Long, PCArtifactDeployLog> pcArtifactDeployLogMap,  PCArtifactDeployLogRepo deployLogRepo, boolean isImport,
                                    String clientId, String tenantName, SAPPublicCloudService sapPcService, PCConnConfig pcConnConfig, PCBrowserClient pcBrowserClient) {
            this.pcArtifactList = pcArtifactList;
            this.pcArtifactDeployLogMap = pcArtifactDeployLogMap;
            this.deployLogRepo = deployLogRepo;
            this.isImport = isImport;
            this.clientId = clientId;
            this.tenantName = tenantName;
            this.sapPcService = sapPcService;
            this.pcConnConfig = pcConnConfig;
            this.pcBrowserClient = pcBrowserClient;
        }

        @Override
        public String getThreadName() {
            return "PC-Import-Status-poll";
        }

        @Override
        public void setThreadContext() {
            MDC.put("tenant", tenantName);

        }

        @Override
        public Map<Long, PCArtifactDeployLog> call() throws Exception {
            List<PCArtifact> pcArtList = new ArrayList<>(this.pcArtifactList);
            for (int i = 0; i < this.iterations && !ObjectUtils.isEmpty(pcArtList); i++) {
                if (isImport) {
                    Thread.sleep(10000);
                }
                Iterator<PCArtifact> iteratorPcArt = pcArtList.iterator();
                while (iteratorPcArt.hasNext()) {
                    PCArtifact art = iteratorPcArt.next();
                    PCArtifactDeployLog deployLog = this.pcArtifactDeployLogMap.get(art.getPcArtifactVersion().getRoVersionId());
                    try {
                        RuntimeImportArtifact runtimeImportArtifact = sapPcService.getImportPcArtifactDetails(deployLog.getSapPcArtifactId(), deployLog.getVersion(), clientId, pcConnConfig, pcBrowserClient);
                        // "R"  --> Means Ready to Import
                        // "I"  --> Means Imported
                        // "Z"  --> Means Importing
                        // "O"  --> Means OutDated
                        if (!isImport) {
                            if ("R".equalsIgnoreCase(runtimeImportArtifact.getImportStatus())) {
                                deployLog.setDeployStatus(PCArtifactDeployLogStatus.IN_PROGRESS);
                            } else if ("I".equalsIgnoreCase(runtimeImportArtifact.getImportStatus())) {
                                deployLog.setAlreadyDeployedManualInSap(true);
                                deployLog.setDeployStatus(PCArtifactDeployLogStatus.SUCCESS);
                            } else {
                                deployLog.setDeployStatus(PCArtifactDeployLogStatus.FAILED);
                                deployLog.setDeployErrorMsg(String.format("Collection status is not 'Ready to Import', Current status: %s", runtimeImportArtifact.getImportStatusDesc()));
                            }
                            deployLog = deployLogRepo.save(deployLog);
                            iteratorPcArt.remove();
                        } else {
                            if ("I".equalsIgnoreCase(runtimeImportArtifact.getImportStatus())) {
                                deployLog.setDeployStatus(PCArtifactDeployLogStatus.SUCCESS);
                                deployLog = deployLogRepo.save(deployLog);
                                iteratorPcArt.remove();

                            } else if ("Z".equalsIgnoreCase(runtimeImportArtifact.getImportStatus())) {
                                // don't save  deploy log during importing stage, save ofter completion of the thread.
                                deployLog.setDeployStatus(PCArtifactDeployLogStatus.TIMEOUT);
                                deployLog.setDeployErrorMsg(String.format("The Import has been triggered, but the status remains '%s'. Please check the status manually in the Public Cloud environment.", runtimeImportArtifact.getImportStatusDesc()));
                            } else {
                                deployLog.setDeployStatus(PCArtifactDeployLogStatus.FAILED);
                                deployLog.setDeployErrorMsg(String.format("The Import has been triggered, but the status remains '%s'. Please check the status manually in the Public Cloud environment.", runtimeImportArtifact.getImportStatusDesc()));
                                deployLog = deployLogRepo.save(deployLog);
                                iteratorPcArt.remove();

                            }
                        }
                    } catch (Throwable t) {
                        String errorMsg = ExceptionUtils.getRootCauseMessage(t);
                        if (errorMsg.toLowerCase().contains("404 not found")) {
                            Thread.sleep(10000);
                        } else {
                            log.error("Exception While getting target collections with error msg {}", errorMsg, t);
                            deployLog.setDeployStatus(PCArtifactDeployLogStatus.FAILED);
                            deployLog.setDeployErrorMsg(errorMsg);
                            deployLog = deployLogRepo.save(deployLog);
                            iteratorPcArt.remove();
                        }
                    }
                    pcArtifactDeployLogMap.put(deployLog.getRoPcArtifactVersionId(), deployLog);
                }
            }
            return pcArtifactDeployLogMap;
        }

    }
}
