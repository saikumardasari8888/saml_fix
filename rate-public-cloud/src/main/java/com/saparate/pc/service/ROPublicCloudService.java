package com.saparate.pc.service;

import tools.jackson.databind.JsonNode;
import com.rate.commons.DateUtil;
import org.springframework.data.domain.Page;
import com.rate.commons.util.sync.SyncObjectsComparator;
import com.rate.commons.util.sync.SyncObjectsResult;
import com.rate.commons.util.sync.SyncStatus;
import com.rate.user.entity.Credential;
import com.rate.user.entity.Customer;
import com.rate.user.services.CredentialService;
import com.saparate.pc.client.PCBrowserClient;
import com.saparate.pc.config.PCConnConfig;
import com.saparate.pc.entity.PCArtifact;
import com.saparate.pc.entity.PCArtifactDeployLog;
import com.saparate.pc.entity.PCArtifactSyncLog;
import com.saparate.pc.entity.PCArtifactVersion;
import com.saparate.pc.entity.PCEnvironment;
import com.saparate.pc.entity.PCTRObject;
import com.saparate.pc.enums.PCArtifactDeployLogStatus;
import com.saparate.pc.exception.ROPublicCloudException;
import com.saparate.pc.model.PCArtifactType;
import com.saparate.pc.model.PCArtifactValidationResult;
import com.saparate.pc.model.PCDependencyValidationReport;
import com.saparate.pc.model.PCTransport;
import com.saparate.pc.model.PCValidationReport;
import com.saparate.pc.model.RoODataBatchResponse;
import com.saparate.pc.model.RuntimeImportArtifact;
import com.saparate.pc.repository.PCArtifactDeployLogRepo;
import com.saparate.pc.repository.PCArtifactRepo;
import com.saparate.pc.repository.PCArtifactSyncLogRepo;
import com.saparate.pc.repository.PCArtifactVersionRepo;
import com.saparate.pc.repository.PCEnvironmentRepo;
import com.saparate.pc.repository.PCMysqlDAO;
import com.saparate.pc.repository.PCTRObjectRepo;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Getter
@Service
public class ROPublicCloudService {

    @Autowired
    private PCEnvironmentRepo pcEnvironmentRepo;

    @Autowired
    private PCArtifactSyncLogRepo pcArtifactSyncLogRepo;

    @Autowired
    private SAPPublicCloudService sapPublicCloudService;

    @Autowired
    private PCArtifactRepo pcArtifactRepo;

    @Autowired
    private PCArtifactVersionRepo pcArtifactVersionRepo;

    @Autowired
    private CredentialService credentialService;

    @Autowired
    private PCMysqlDAO pcMysqlDAO;

    @Autowired
    private PCArtifactDeployLogRepo pcArtifactDeployLogRepo;

    @Autowired
    private PCTRObjectRepo pctrObjectRepo;


    public PCEnvironment addOrUpdatePublicCloudEnv(PCEnvironment pcEnv, Customer userObj) {

        String userName = userObj.getUsername();
        Date currentDate = new Date();
        pcEnv.setLastModifiedBy(userName);
        pcEnv.setLastModifiedDate(currentDate);
        if (pcEnv.getId() > 0) {
            PCEnvironment existPcEnv = pcEnvironmentRepo.findByTenantNameAndId(userObj.getTenantName(), pcEnv.getId()).orElse(null);
            if (existPcEnv != null) {
                pcEnv.setCreatedBy(existPcEnv.getCreatedBy());
                pcEnv.setCreationDate(existPcEnv.getCreationDate());
            } else {
                log.warn("Record not found for addOrUpdate id : {}", pcEnv.getId());
                throw new IllegalArgumentException(String.format("Public Cloud Environment data was not found for id : %s", pcEnv.getId()));
            }
        } else {
            pcEnv.setCreatedBy(userName);
            pcEnv.setCreationDate(currentDate);
        }
        pcEnv = pcEnvironmentRepo.save(pcEnv);
        return pcEnv;
    }

    public List<PCEnvironment> getAllPublicCloudEnvByTenantName(String tenantName) {
        return pcEnvironmentRepo.findAllByTenantName(tenantName);
    }

    public PCEnvironment getPublicCloudEnvById(String tenantName, long id) {
        return pcEnvironmentRepo.findByTenantNameAndId(tenantName, id).orElse(null);
    }

    public void deletePublicCloudEnvById(String tenantName, long id) {
        pcEnvironmentRepo.deleteByTenantNameAndId(tenantName, id);
    }

    public Page<PCArtifact> getPcArtifactsInTenantEnv(String tenantName, long projectId, long envId, int pageNo, int pageSize) {
        log.info("Processing request for getting all Public Cloud Artifacts for the tenant {} and env {}", tenantName, envId);
        Pageable pageable = PageRequest.of(pageNo - 1, pageSize);
        return pcArtifactRepo.findByTenantNameAndProjectIdAndEnvIdAndIsDeletedOrderByRoIdDesc(tenantName, projectId, envId, false, pageable);
    }

    public List<PCArtifactSyncLog> checkSyncLogInProgress(String tenantName, long envId) {
        return pcArtifactSyncLogRepo.findAllByTenantNameAndEnvIdAndSyncStatus(tenantName, envId, SyncStatus.IN_PROGRESS);
    }

    public PCArtifactSyncLog initializeSyncLog(Customer userObj, long projectId, long envId) {
        PCArtifactSyncLog syncLog = new PCArtifactSyncLog();
        Date date = new Date();
        syncLog.setTenantName(userObj.getTenantName());
        syncLog.setEnvId(envId);
        syncLog.setProjectId(projectId);
        syncLog.setSyncStatus(SyncStatus.IN_PROGRESS);
        syncLog.setType("PcArtifact");
        syncLog.setCreatedBy(userObj.getEmail());
        syncLog.setCreationDate(date);
        syncLog.setLastModifiedBy(userObj.getEmail());
        syncLog.setLastModifiedDate(date);
        return syncLog;
    }

    public void syncPublicCloudArtifacts(Customer userObj, long projectId, long envId) throws Exception {
        String tenantName = userObj.getTenantName();
        PCEnvironment pcEnv = this.getPublicCloudEnvById(userObj.getTenantName(), envId);
        if (pcEnv == null) {
            throw new Exception("Public Cloud Environment with Id " + envId + " is not present");
        }
        Credential credential = credentialService.getCredentialById(pcEnv.getCredentialId());
        if (credential == null) {
            throw new Exception("Public Cloud Credential with Id " + pcEnv.getCredentialId() + " is not present");
        }
        PCConnConfig config = new PCConnConfig(pcEnv.getHostUrl(), credential.getUserName(), credential.getActualPassword());
        try {
            PCBrowserClient pcBrowserClient = new PCBrowserClient();
            log.info("Fetching Public Cloud Transport Artifacts from host {} with credId {}", pcEnv.getHostUrl(), pcEnv.getCredentialId());
            List<PCArtifact> newPcArtifacts = new ArrayList<>();
            List<PCArtifact> newPcTrArtifacts = sapPublicCloudService.getPcTrArtifacts(projectId, envId, pcEnv.getClientId(), config, pcBrowserClient);
            log.info("Total Public Cloud Transport Artifacts  fetched {}", newPcTrArtifacts.size());
            if (!ObjectUtils.isEmpty(newPcTrArtifacts)) {
                newPcArtifacts.addAll(newPcTrArtifacts);
            }
            log.info("Fetching Public Cloud Software Artifacts from host {} with credId {}", pcEnv.getHostUrl(), pcEnv.getCredentialId());
            List<PCArtifact> newPcSoftwareArtifacts = sapPublicCloudService.getPcSoftWareArtifacts(projectId, envId, pcEnv.getClientId(), config, pcBrowserClient);
            log.info("Total Public Cloud Software Artifacts  fetched {}", newPcSoftwareArtifacts.size());
            if (!ObjectUtils.isEmpty(newPcSoftwareArtifacts)) {
                newPcArtifacts.addAll(newPcSoftwareArtifacts);
            }

            List<PCArtifact> oldPcArtifacts = getPublicCloudArtifactsInTenantEnv(tenantName, projectId, envId);
            SyncObjectsComparator<PCArtifact> syncObjectsComparator = new SyncObjectsComparator<>(new PCArtifactObjectSyncHelper(userObj), false);
            SyncObjectsResult<PCArtifact> syncResult = syncObjectsComparator.findDiff(oldPcArtifacts, newPcArtifacts);

            log.info("Saving added  Public Cloud Artifacts count {}", syncResult.getAddedObjects().size());
            List<PCArtifact> addedPcArtifacts = pcArtifactRepo.saveAll(syncResult.getAddedObjects());
            savePublicCloudArtifactVersions(addedPcArtifacts, true);
            log.info("Saving modified Public Cloud Artifacts count {}", syncResult.getModifiedObjects().size());
            savePublicCloudArtifactVersions(syncResult.getModifiedObjects(), false);
            pcArtifactRepo.saveAll(syncResult.getModifiedObjects());
            log.info("Saving deleted Public Cloud Artifacts count {}", syncResult.getDeletedObjects().size());
            for (PCArtifact pcItem : syncResult.getDeletedObjects()) {
                // Mock deletion is applied only for Software Collections; Transport requests are not deleted in SAP system.
                if(PCArtifactType.SOFTWARE_COLLECTION == pcItem.getType()) {
                    pcItem.setDeleted(true);
                }
            }
            pcArtifactRepo.saveAll(syncResult.getDeletedObjects());
        } catch (Throwable t) {
            String msg = String.format("Failed to fetch Public CloudArtifacts from host %s with envId %s. Error: %s", pcEnv.getHostUrl(), envId, t.getMessage());
            log.error(msg, t);
            throw new Exception(msg, t);
        }
    }

    public void syncPcSoftwareCollectionById(Customer userObj, long projectId, long envId, PCArtifact pcArtifact) throws Exception {

        PCEnvironment pcEnv = this.getPublicCloudEnvById(userObj.getTenantName(), envId);
        if (pcEnv == null) {
            throw new Exception("Public Cloud Environment with Id " + envId + " is not present");
        }
        Credential credential = credentialService.getCredentialById(pcEnv.getCredentialId());
        if (credential == null) {
            throw new Exception("Public Cloud Credential with Id " + pcEnv.getCredentialId() + " is not present");
        }
        PCConnConfig config = new PCConnConfig(pcEnv.getHostUrl(), credential.getUserName(), credential.getActualPassword());
        try {
            PCBrowserClient pcBrowserClient = new PCBrowserClient();
            log.info("Fetching Public Cloud Software Artifacts from host {} with credId {}", pcEnv.getHostUrl(), pcEnv.getCredentialId());
            PCArtifact newPcArtifact = sapPublicCloudService.getPcSoftwareLatestExportVersion(pcArtifact.getArtifactId(),  pcEnv.getClientId(),  config, projectId, envId, pcBrowserClient);
            List<PCArtifact> newPcArtifacts = Collections.singletonList(newPcArtifact);
            List<PCArtifact> oldPcArtifacts = Collections.singletonList(pcArtifact);
            SyncObjectsComparator<PCArtifact> syncObjectsComparator = new SyncObjectsComparator<>(new PCArtifactObjectSyncHelper(userObj), false);
            SyncObjectsResult<PCArtifact> syncResult = syncObjectsComparator.findDiff(oldPcArtifacts, newPcArtifacts);

            log.info("Saving added  Public Cloud Artifacts count {}", syncResult.getAddedObjects().size());
            List<PCArtifact> addedPcArtifacts = pcArtifactRepo.saveAll(syncResult.getAddedObjects());
            savePublicCloudArtifactVersions(addedPcArtifacts, true);
            log.info("Saving modified Public Cloud Artifacts count {}", syncResult.getModifiedObjects().size());
            savePublicCloudArtifactVersions(syncResult.getModifiedObjects(), false);
            pcArtifactRepo.saveAll(syncResult.getModifiedObjects());
            log.info("Saving deleted Public Cloud Artifacts count {}", syncResult.getDeletedObjects().size());
            for (PCArtifact pcItem : syncResult.getDeletedObjects()) {
                pcItem.setDeleted(true);
            }
            pcArtifactRepo.saveAll(syncResult.getDeletedObjects());
        } catch (Throwable t) {
            String msg = String.format("Failed to fetch Public CloudArtifacts from host %s with envId %s. Error: %s", pcEnv.getHostUrl(), envId, t.getMessage());
            log.error(msg, t);
            throw new Exception(msg, t);
        }
    }

    public List<PCArtifact> getPublicCloudArtifactsInTenantEnv(String tenantName, long projectId, long envId) {
        log.info("Processing request for getting all Public Cloud Artifacts for the tenant {} and env {}", tenantName, envId);
        return pcArtifactRepo.findByTenantNameAndProjectIdAndEnvIdAndIsDeletedOrderByRoIdDesc(tenantName, projectId, envId, false);
    }

    private void savePublicCloudArtifactVersions(List<PCArtifact> addedPcArtifacts, boolean isAdded) {
        if (addedPcArtifacts != null) {
            List<PCArtifactVersion> pcArtifactVersions = new ArrayList<>();
            for (PCArtifact PCArtifact : addedPcArtifacts) {
                PCArtifactVersion version = PCArtifact.getPcArtifactVersion();
                if (version != null) {
                    version.setRoArtifactId(PCArtifact.getRoId());
                    pcArtifactVersions.add(version);
                }
            }
            if (isAdded) {
                pcArtifactVersionRepo.saveAll(pcArtifactVersions);
            }
        }
    }

    public List<PCArtifactSyncLog> getPcArtifactsSyncHistory(Customer userObj, long envId, long projectId) {
        return pcArtifactSyncLogRepo.findAllByTenantNameAndEnvIdAndProjectIdOrderByCreationDateDesc(userObj.getTenantName(), envId, projectId);
    }

    public List<PCArtifactVersion> getPcArtifactVersions(String tenantName, long projectId, long pcArtifactId) {
        return pcMysqlDAO.getPcArtifactVersions(tenantName, projectId, pcArtifactId);
    }

    public List<PCArtifact> getPcArtifactWithVersions(String tenantName, List<Long> roPcArtVersionIds) {
        return pcMysqlDAO.getPcArtifactWithVersions(tenantName, roPcArtVersionIds);
    }

    public PCEnvironment getPcEnvironment(String tenantName, long id) {
        return pcEnvironmentRepo.findByTenantNameAndId(tenantName, id).orElse(null);
    }

    public PCArtifactSyncLog savePcArtifactSyncLog(PCArtifactSyncLog syncLog) {
        return pcArtifactSyncLogRepo.save(syncLog);
    }

    public List<PCArtifactDeployLog> getPcDeployLog(String wfTaskId, String tenantName) {
        return pcArtifactDeployLogRepo.findAllByTenantNameAndWfTaskIdOrderByIdAsc(tenantName, wfTaskId);
    }

    public PCArtifactDeployLog updatePCDeployLogManual(PCArtifactDeployLog pcDeployLog, Customer userObj) {
        Date currentDate = new Date();
        pcDeployLog.setDeployedType("Manual");
        pcDeployLog.setManualDeployDate(currentDate);
        pcDeployLog.setManualDeployedBy(userObj.getUsername());
        pcDeployLog.setLastModifiedDate(currentDate);
        pcDeployLog.setLastModifiedBy(userObj.getUsername());
        pcArtifactDeployLogRepo.updateDeployLogManual(pcDeployLog.getId(), pcDeployLog.getDeployedType(), pcDeployLog.getManualDeployedBy(), pcDeployLog.getManualDeployedReason(), pcDeployLog.getManualDeployDate(), pcDeployLog.getLastModifiedDate(), pcDeployLog.getLastModifiedBy());
//        this.writeCpiManualDeployedCommentInAlm(cpiDeployLog, userObj, currentDate);
        return pcDeployLog;
    }

    public List<PCArtifactDeployLog> updatePCArtifactDeployStatusInDeployLog(Customer userObj, long projectId, String wfTaskId) throws Exception {
        List<PCArtifactDeployLog> deployLogList = pcArtifactDeployLogRepo.findAllByTenantNameAndProjectIdAndWfTaskIdAndDeployStatus(userObj.getTenantName(), projectId, wfTaskId, PCArtifactDeployLogStatus.TIMEOUT);
        if (ObjectUtils.isEmpty(deployLogList)) {
            return deployLogList;
        }
        PCEnvironment pcEnv = pcEnvironmentRepo.findByTenantNameAndId(userObj.getTenantName(), deployLogList.getFirst().getPcDeployEnvId()).orElse(null);
        if (pcEnv == null) {
            throw new Exception("Public Cloud Environment with Id " + deployLogList.getFirst().getPcDeployEnvId() + " is not present");
        }
        Credential credential = credentialService.getCredentialById(pcEnv.getCredentialId());
        if (credential == null) {
            throw new Exception("Public Cloud Credential with Id " + pcEnv.getCredentialId() + " is not present");
        }

        PCConnConfig config = new PCConnConfig(pcEnv.getHostUrl(), credential.getUserName(), credential.getActualPassword());
        PCBrowserClient pcBrowserClient = new PCBrowserClient();
        Date currentDate = new Date();
        for (PCArtifactDeployLog deployLog : deployLogList) {
            // If the artifact has already been manually completed, skip it while fetching the runtime deploy status.
            if(!ObjectUtils.isEmpty(deployLog.getDeployedType()) && "Manual".equalsIgnoreCase(deployLog.getDeployedType())) {
                continue;
            }
            RuntimeImportArtifact importStatusArt = sapPublicCloudService.getImportPcArtifactDetails(deployLog.getSapPcArtifactId(), deployLog.getVersion(), pcEnv.getClientId(), config, pcBrowserClient);
            deployLog.setTenantName(userObj.getTenantName());
            if ("I".equalsIgnoreCase(importStatusArt.getImportStatus())) {
                deployLog.setDeployStatus(PCArtifactDeployLogStatus.SUCCESS);
                deployLog.setTenantName(userObj.getTenantName());
                deployLog.setLastModifiedBy(userObj.getUsername());
                deployLog.setLastModifiedDate(currentDate);
                pcArtifactDeployLogRepo.save(deployLog);
            } else if (!("Z".equalsIgnoreCase(importStatusArt.getImportStatus()))) {
                deployLog.setDeployStatus(PCArtifactDeployLogStatus.FAILED);
                deployLog.setLastModifiedBy(userObj.getUsername());
                deployLog.setLastModifiedDate(currentDate);
                pcArtifactDeployLogRepo.save(deployLog);
            }

        }
        return deployLogList;
    }

    public PCValidationReport validatePcArtifacts(List<PCArtifact> pcArtifactList, Customer userObj, long projectId, long envId) throws ROPublicCloudException {
        try {
            PCEnvironment pcEnv = pcEnvironmentRepo.findByTenantNameAndId(userObj.getTenantName(), envId).orElse(null);
            if (pcEnv == null) {
                throw new ROPublicCloudException("Public Cloud Environment with Id " + envId + " is not present");
            }
            Credential credential = credentialService.getCredentialById(pcEnv.getCredentialId());
            if (credential == null) {
                throw new ROPublicCloudException("Public Cloud Credential with the name " + pcEnv.getName() + " could not be found.");
            }
            PCConnConfig config = new PCConnConfig(pcEnv.getHostUrl(), credential.getUserName(), credential.getActualPassword());
            PCBrowserClient client = new PCBrowserClient();
             List<PCArtifactValidationResult> pcValidationReports = new ArrayList<>();
             List<PCDependencyValidationReport> pcAllDependencyReports = new ArrayList<>();
             // validate each artifact to get actual validation of that artifact.
            for (PCArtifact art : pcArtifactList) {
                List<RoODataBatchResponse> responses = sapPublicCloudService.validateImportPcArtifact(Collections.singletonList(art), pcEnv.getClientId(), config, client);
                Map<String, List<JsonNode>> pcValidationMap = parsePcValidationMap(responses);
                this.preparePcValidationReport(pcArtifactList, art.getArtifactId(), userObj.getTenantName(), projectId, pcValidationMap, pcValidationReports, pcAllDependencyReports, pcEnv.getClientId(), config, client);
            }
            PCValidationReport pcValidationMode = new PCValidationReport();
            pcValidationMode.setPcValidationReport(pcValidationReports);
            pcValidationMode.setPcDependencyReport(pcAllDependencyReports);

            return pcValidationMode;
        } catch (Throwable t) {
            String errorMsg = String.format("Exception while validate public cloud artifact with msg %s", ExceptionUtils.getRootCauseMessage(t));
            log.error(errorMsg, t);
            throw new ROPublicCloudException(errorMsg);
        }
    }

    public Map<String, List<JsonNode>> parsePcValidationMap(List<RoODataBatchResponse> oDateRes) {
        Map<String, List<JsonNode>> groupedMap = new LinkedHashMap<>();
        for (RoODataBatchResponse item : oDateRes) {
            JsonNode dNode = item.getJsonBody().path("d");
            if (!dNode.isObject()) continue;

            if (dNode.has("results")) {
                // Extract type from metadata: "Collection(APS_EXT_ATO_IMP_SRV.SomeType)"
                String type = dNode.path("__metadata").path("type").asText();
                String key = type.substring(type.lastIndexOf('.') + 1).replace(")", "");

                List<JsonNode> results = new ArrayList<>();
                dNode.path("results").forEach(results::add);
                groupedMap.computeIfAbsent(key, k -> new ArrayList<>()).addAll(results);
            } else {
                dNode.properties().forEach(field -> {
                    String key = field.getKey();
                    groupedMap.computeIfAbsent(key, k -> new ArrayList<>()).add(field.getValue());
                });
            }
        }
        return groupedMap;
    }

    public void preparePcValidationReport(List<PCArtifact> pcArtifactList, String pcArtifactId, String tenantName, long projectId, Map<String, List<JsonNode>> pcValidationMap,
                                          List<PCArtifactValidationResult> pcValidationReports,  List<PCDependencyValidationReport>  pcAllDependencyReports,
                                          String clientId, PCConnConfig config, PCBrowserClient client) throws Exception {
        List<JsonNode> filterVersionList = pcValidationMap.get("FilteredCollectionVersionsToBeImported");
        List<JsonNode> dependentList = pcValidationMap.get("DependentCollectionVersionsToBeImported");
        List<JsonNode> validationCheckResult = pcValidationMap.get("ValidationCheckResult");
        List<JsonNode> importSummary = pcValidationMap.get("CommonImportSummary");
        PCArtifactValidationResult pcArtValidationRes = new PCArtifactValidationResult();
        String roStatus = null;
        String message = null;
        if (!ObjectUtils.isEmpty(filterVersionList)) {
            for (JsonNode fNode : filterVersionList) {
                String artifactId = fNode.get("CollectionId").asText();
                String version = fNode.get("Version").asText();
                pcArtValidationRes.setArtifactId(artifactId);
                // if Transport not present in the target env.
                if (ObjectUtils.isEmpty(artifactId)) {
                    PCArtifact pcArt = pcArtifactList.stream()
                            .filter(art -> pcArtifactId.equals(art.getArtifactId()))
                            .findFirst()
                            .orElse(null);
                    assert pcArt != null;
                    String artTypeDesc = "Key User Extensibility"; // this is for software collection
                    if (PCArtifactType.TRANSPORT == pcArt.getType()) {
                        PCTransport pcTrDetails = (PCTransport) pcArt.getPcArtifactVersion().getPcArtifactDetails();
                        artTypeDesc = pcTrDetails.getAtoTransportType();
                    }
                    pcArtValidationRes.setArtifactId(pcArt.getArtifactId());
                    pcArtValidationRes.setDescription(pcArt.getName());
                    pcArtValidationRes.setVersion(pcArt.getPcArtifactVersion().getVersion());
                    pcArtValidationRes.setArtifactTypeDescription(artTypeDesc);
                    pcArtValidationRes.setStatusDescription("Not present in target");
                    pcArtValidationRes.setStatus("Failed");
                    pcArtValidationRes.setMessage(String.format("The collection ID '%s' is not found in the target environment.", pcArtifactId));
                    pcValidationReports.add(pcArtValidationRes);
                    return;
                }
                pcArtValidationRes.setVersion(version);
                pcArtValidationRes.setDescription(fNode.get("Description").asText());
                pcArtValidationRes.setStatusDescription(fNode.get("StatusDescription").asText());
                pcArtValidationRes.setArtifactTypeDescription(fNode.get("CollectionTypeDescription").asText());
                pcArtValidationRes.setDowntimeDescription(fNode.get("DowntimeDescription").asText());
                pcArtValidationRes.setExportedDate(DateUtil.convertSdsDateTime(fNode.get("ExportedAt").asText()));
                String status = fNode.get("Status").asText();
                pcArtValidationRes.setPcStatus(status);

                if("I".equalsIgnoreCase(status)) {
                    roStatus = "Info";
                    message = String.format("The collection '%s' with the version '%s' is already imported, it will not import via ReleaseOwl.", artifactId, version);
                }  else if("R".equalsIgnoreCase(status)) {
                    roStatus = "Success";
                    message = "Successfully validated.";
                } else if ("O".equalsIgnoreCase(status)) {
                    roStatus = "Failed";
                    message = String.format("Collection '%s' Collect version '%s' has status Outdated. It can't be imported.", artifactId, version);
                } else {
                    roStatus = "Failed";
                    message = String.format("Validation failed for the collection '%s' and version '%s'.", artifactId, version);
                    if(!ObjectUtils.isEmpty(validationCheckResult)) {
                        message = validationCheckResult.stream()
                                .map(node -> node.get("Description").asText())
                                .collect(Collectors.joining("\n"));
                    }
                }


            }
        }
        List<PCArtifactValidationResult> pcIndividualUsDependentArtList = new ArrayList<>();
        if(!ObjectUtils.isEmpty(dependentList)) {
            List<JsonNode> sortedDependentNode = this.sortDependentNodeByDate(dependentList);
            this.getDependentArtifactChain(tenantName, projectId, pcArtValidationRes.getArtifactId(), pcArtifactList, sortedDependentNode, pcIndividualUsDependentArtList, clientId, config, client);
        }
        if(!ObjectUtils.isEmpty(pcIndividualUsDependentArtList)) {
            roStatus = "Failed";
            message = String.format("Validation failed, the collection '%s' and version '%s' contains dependent collection.", pcArtValidationRes.getArtifactId(), pcArtValidationRes.getVersion());
            PCDependencyValidationReport pcDependent = new PCDependencyValidationReport();
            pcDependent.setArtifactId(pcArtValidationRes.getArtifactId());
            pcDependent.setDependencies(pcIndividualUsDependentArtList);
            pcAllDependencyReports.add(pcDependent);
        }
        pcArtValidationRes.setStatus(roStatus);
        pcArtValidationRes.setMessage(message);
        pcValidationReports.add(pcArtValidationRes);
    }

    private void getDependentArtifactChain(String tenantName, long projectId, String sourceArtifactId, List<PCArtifact> pcArtifactList, List<JsonNode> dependentList,
                                           List<PCArtifactValidationResult> pcIndividualUsDependentArtList, String clientId, PCConnConfig config, PCBrowserClient client) throws Exception {
        if (!ObjectUtils.isEmpty(dependentList)) {
            return;
        }
        Iterator<JsonNode> iterator = dependentList.iterator();
        while (iterator.hasNext()) {
            JsonNode dNode = iterator.next();
            String dependentArtifactId = dNode.get("CollectionId").asText();
            String version = dNode.get("Version").asText();
            // If the artifact is found in the user story artifacts list, ignore it.
            boolean isArtifactPresentInThisValidation = pcArtifactList.stream()
                    .anyMatch(art -> dependentArtifactId.equalsIgnoreCase(art.getArtifactId()));
            if(isArtifactPresentInThisValidation) {
                /**
                 * Gets all dependent artifacts for transport validation.
                 * Example: TR1 depends on TR2, and TR2 depends on TR3.
                 * When validating TR1, it returns TR2 and TR3.
                 * But if TR2 is already part of the same user story,we remove TR2 and TR3 from TR1's dependencies,
                 * so we don't show them again.
                 */
                PCArtifact dependentPcArtifact = new PCArtifact();
                dependentPcArtifact.setArtifactId(dependentArtifactId);
                PCArtifactVersion dependentPcVersion = new PCArtifactVersion();
                dependentPcVersion.setVersion(version);
                dependentPcArtifact.setPcArtifactVersion(dependentPcVersion);
                List<RoODataBatchResponse> responses = sapPublicCloudService.getPcDependentArtifact(Collections.singletonList(dependentPcArtifact), clientId, config, client);
                Map<String, List<JsonNode>> pcValidationMap = parsePcValidationMap(responses);
                List<JsonNode> dependentPcArtList = pcValidationMap.get("DependentCollectionVersionsToBeImported");
                if (!ObjectUtils.isEmpty(dependentPcArtList)) {
                    List<String> dependentTrList = dependentPcArtList.stream()
                            .map(dep -> dep.get("CollectionId").asText())
                            .collect(Collectors.toList());
                    //  SAFE REMOVE using iterator
                    if (dependentTrList.contains(dependentArtifactId)) {
                        iterator.remove();
                    }
                }
                continue;
            }

            // This is for individual user story artifact dependents.
            // Check whether the dependent artifact exists in pcIndividualUsDependentArtList; if it does, ignore it.
            boolean isAlreadyDependent = pcIndividualUsDependentArtList.stream()
                    .anyMatch(dep -> dependentArtifactId.equalsIgnoreCase(dep.getDependentArtifactId()));
            if (isAlreadyDependent) {
                continue;
            }
            // Create validation result
            PCArtifactValidationResult dependentArt = new PCArtifactValidationResult();
            dependentArt.setArtifactId(sourceArtifactId);
            dependentArt.setVersion(version);
            dependentArt.setDescription(dNode.get("Description").asText());
            dependentArt.setStatusDescription(dNode.get("StatusDescription").asText());
            dependentArt.setArtifactTypeDescription(dNode.get("CollectionTypeDescription").asText());
            dependentArt.setDowntimeDescription(dNode.get("DowntimeDescription").asText());
            dependentArt.setExportedDate(DateUtil.convertSdsDateTime(dNode.get("ExportedAt").asText()));
            dependentArt.setDependentArtifactId(dependentArtifactId);
            // set user story details for the dependent artifact.
            PCArtifact pcArtifact = pcMysqlDAO.getPcArtifactByArtifactIdAndVersion(tenantName, projectId, dependentArtifactId, version);
            if(pcArtifact != null) {
                dependentArt.setUserStoryId(pcArtifact.getPcArtifactVersion().getUserStoryId());
                dependentArt.setProjUsId(pcArtifact.getPcArtifactVersion().getProjUserStoryId());
                dependentArt.setExtUsId(pcArtifact.getPcArtifactVersion().getExtUsId());
            }
            pcIndividualUsDependentArtList.add(dependentArt);

            // Check whether the dependent artifact depends on other artifacts
            PCArtifact dependentPcArtifact = new PCArtifact();
            dependentPcArtifact.setArtifactId(dependentArtifactId);
            PCArtifactVersion dependentPcVersion = new PCArtifactVersion();
            dependentPcVersion.setVersion(version);
            dependentPcArtifact.setPcArtifactVersion(dependentPcVersion);

            List<RoODataBatchResponse> responses = sapPublicCloudService.getPcDependentArtifact(Collections.singletonList(dependentPcArtifact), clientId, config, client);
            Map<String, List<JsonNode>> pcValidationMap = parsePcValidationMap(responses);
            List<JsonNode> dependentPcArtList = pcValidationMap.get("DependentCollectionVersionsToBeImported");
            if (!ObjectUtils.isEmpty(dependentPcArtList)) {
                List<JsonNode> sortedDependentNode = this.sortDependentNodeByDate(dependentPcArtList);
                this.getDependentArtifactChain(tenantName, projectId, dependentArtifactId, pcArtifactList, sortedDependentNode, pcIndividualUsDependentArtList, clientId, config, client);
            }
        }
    }

    private List<JsonNode> sortDependentNodeByDate(List<JsonNode> dependentList) {
        List<JsonNode> sortedList = new ArrayList<>(dependentList);
        sortedList.sort(Comparator.comparingLong(nodeObj -> {
            JsonNode node = (JsonNode) nodeObj;
            String exportedAt = node.get("ExportedAt").asText();
            String timestampStr = exportedAt.substring(6, exportedAt.lastIndexOf(")"));
            return Long.parseLong(timestampStr);
        }).reversed());
        return sortedList;
    }

    public PCTRObject getPcTransportObjects(Customer userObj, long projectId, long roArtId) throws ROPublicCloudException {
        try {
            PCTRObject pctrObject = pctrObjectRepo.findByTenantNameAndProjectIdAndRoArtifactId(userObj.getTenantName(), projectId, roArtId);
            if (pctrObject != null && !ObjectUtils.isEmpty(pctrObject.getPcTrObjects())) {
                return pctrObject;
            }

            PCArtifact pcArtifact = getPcArtifactById(userObj.getTenantName(), projectId, roArtId);
            if (pcArtifact == null) {
                log.error("PC Artifact not found for the roArtifactId {}", roArtId);
                throw new ROPublicCloudException("Collection is not found.");
            }

            PCEnvironment pcEnv = pcEnvironmentRepo.findByTenantNameAndId(userObj.getTenantName(), pcArtifact.getEnvId()).orElse(null);
            if (pcEnv == null) {
                throw new ROPublicCloudException("Public Cloud Environment with Id " + pcArtifact.getEnvId() + " is not present");
            }
            Credential credential = credentialService.getCredentialById(pcEnv.getCredentialId());
            if (credential == null) {
                throw new ROPublicCloudException("Public Cloud Credential with the name " + pcEnv.getName() + " could not be found.");
            }
            PCConnConfig pcConfig = new PCConnConfig(pcEnv.getHostUrl(), credential.getUserName(), credential.getActualPassword());
            PCBrowserClient pcClient = new PCBrowserClient();
            JsonNode pcTrJsonNode = sapPublicCloudService.getPcTransportObject(pcArtifact.getArtifactId(), pcEnv.getClientId(), pcConfig, pcClient);
            PCTRObject trObj = new PCTRObject();
            Date currentDate = new Date();
            if (pctrObject != null) {
                trObj.setId(pctrObject.getId());
                trObj.setCreatedBy(pctrObject.getCreatedBy());
                trObj.setCreationDate(pctrObject.getCreationDate());
            } else {
                trObj.setCreatedBy(userObj.getUsername());
                trObj.setCreationDate(currentDate);
            }
            trObj.setPcTrObjects(pcTrJsonNode);
            trObj.setProjectId(projectId);
            trObj.setRoArtifactId(roArtId);
            trObj.setSapTRId(pcArtifact.getArtifactId());
            trObj.setTrName(pcArtifact.getName());
            trObj.setEnvId(pcArtifact.getEnvId());
            trObj.setTenantName(userObj.getTenantName());
            trObj.setLastModifiedBy(userObj.getUsername());
            trObj.setLastModifiedDate(currentDate);
            trObj = pctrObjectRepo.save(trObj);
            return trObj;
        } catch (Throwable t) {
            log.error("Exception while getting pc transport object for roArtifactId {} with error message {}", roArtId, ExceptionUtils.getRootCauseMessage(t), t);
            throw new ROPublicCloudException(t);
        }
    }

    public PCArtifact getPcArtifactById(String tenantName, long projectId, long roArtifactId) {
        return  pcArtifactRepo.findByTenantNameAndProjectIdAndRoId(tenantName, projectId, roArtifactId);

    }

    public PCArtifact searchPcArtifactById(Customer userObj, String sapArtifactId, long projectId, long envId, boolean isLocalSearch) throws ROPublicCloudException {
        // isLocalSearch - > true   Fetching data from DB.
        // isLocalSearch -> false   Fetching data from SAP Public Cloud.
        PCArtifact pcArtifact = pcArtifactRepo.findByTenantNameAndProjectIdAndEnvIdAndArtifactIdAndIsDeleted(userObj.getTenantName(), projectId, envId, sapArtifactId, false);
        if (pcArtifact != null) {
            return pcArtifact;
        }
        if(!isLocalSearch) {
            pcArtifact = this.getPcTransportById(userObj, sapArtifactId, projectId, envId);
            pcArtifact =  pcArtifactRepo.save(pcArtifact);
            pcArtifact.getPcArtifactVersion().setRoArtifactId(pcArtifact.getRoId());
            pcArtifactVersionRepo.save(pcArtifact.getPcArtifactVersion());
            return pcArtifact;
        } else {
            throw new ROPublicCloudException(String.format("The collection '%s' was not found. Please sync the collections.", sapArtifactId));
        }
    }

    public PCArtifact getPcTransportById(Customer userObj, String sapTransportId, long projectId, long envId) throws ROPublicCloudException {
        log.info("get the request for getting public cloud customizing transport for the TR id {}", sapTransportId);
        PCEnvironment pcEnv = this.getPublicCloudEnvById(userObj.getTenantName(), envId);
        if (pcEnv == null) {
            throw new ROPublicCloudException("Public Cloud Environment with Id " + envId + " is not present");
        }
        Credential credential = credentialService.getCredentialById(pcEnv.getCredentialId());
        if (credential == null) {
            throw new ROPublicCloudException("Public Cloud Credential with Id " + pcEnv.getCredentialId() + " is not present");
        }
        PCConnConfig config = new PCConnConfig(pcEnv.getHostUrl(), credential.getUserName(), credential.getActualPassword());
        try {
            PCBrowserClient pcBrowserClient = new PCBrowserClient();
            PCArtifact pcArtifact = null;
            // Fetching Transport data from SAP Public Cloud
            try {
                pcArtifact = sapPublicCloudService.getPcTransportById(projectId, envId, sapTransportId, pcEnv.getClientId(), config, pcBrowserClient);

            } catch (Throwable t) {
                log.error("Exception while getting public cloud transport {} with error {}", sapTransportId, ExceptionUtils.getRootCauseMessage(t), t);
            }
            if (pcArtifact != null) {
                PCTransport pcTransport = (PCTransport) pcArtifact.getPcArtifactVersion().getPcArtifactDetails();
                if (!("R".equalsIgnoreCase(pcTransport.getTransportRequestStatusOld()))) {
                    throw new ROPublicCloudException(String.format("The public cloud transport '%s' is in '%s' status in the environment '%s'. Please release it.", sapTransportId, pcTransport.getTransportRequestStatusText(), pcEnv.getName()));

                }
            }
            // Fetching Software Collection data from SAP Public Cloud
            if (pcArtifact == null) {
                pcArtifact = sapPublicCloudService.getPcTransportById(projectId, envId, sapTransportId, pcEnv.getClientId(), config, pcBrowserClient);
            }
            if (pcArtifact == null) {
                throw new ROPublicCloudException(String.format("Public cloud collection '%s' is not found in environment '%s'.", sapTransportId, pcEnv.getName()));
            }

            PCArtifactVersion pcVersion = pcArtifact.getPcArtifactVersion();
            pcArtifact.setTenantName(userObj.getTenantName());
            Date dt = new Date();
            pcArtifact.setCreatedBy(userObj.getEmail());
            pcArtifact.setCreationDate(dt);
            pcArtifact.setLastModifiedDate(dt);
            pcArtifact.setLastModifiedBy(userObj.getEmail());
            pcVersion.setCreationDate(dt);
            pcVersion.setCreatedBy(userObj.getEmail());
            pcVersion.setTenantName(userObj.getTenantName());
            pcVersion.setLastModifiedDate(dt);
            pcVersion.setLastModifiedBy(userObj.getEmail());
            return pcArtifact;
        } catch (Throwable t) {
            log.error("Exception while getting public cloud collection {} with error {}", sapTransportId, ExceptionUtils.getRootCauseMessage(t), t);
            throw new ROPublicCloudException(t);
        }
    }
}
