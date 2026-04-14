package com.saparate.pc.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.rate.commons.DateUtil;
import com.saparate.pc.client.CBCBrowserClient;
import com.saparate.pc.client.PCBrowserClient;
import com.saparate.pc.config.PCConnConfig;
import com.saparate.pc.entity.CBCArtifact;
import com.saparate.pc.entity.CBCArtifactVersion;
import com.saparate.pc.entity.PCArtifact;
import com.saparate.pc.entity.PCArtifactVersion;
import com.saparate.pc.enums.CBCActionType;
import com.saparate.pc.model.CBCAction;
import com.saparate.pc.model.CBCActionDetails;
import com.saparate.pc.model.CBCPayLoadObj;
import com.saparate.pc.model.CBCTargetSystemDetails;
import com.saparate.pc.model.CBCWorkSpace;
import com.saparate.pc.model.PCArtifactType;
import com.saparate.pc.model.PCSoftware;
import com.saparate.pc.model.PCSoftwareItemDetails;
import com.saparate.pc.model.PCTransport;
import com.saparate.pc.model.RoODataBatchResponse;
import com.saparate.pc.model.RuntimeImportArtifact;
import org.apache.olingo.odata2.api.client.batch.BatchChangeSet;
import org.apache.olingo.odata2.api.client.batch.BatchChangeSetPart;
import org.apache.olingo.odata2.api.client.batch.BatchPart;
import org.apache.olingo.odata2.api.client.batch.BatchSingleResponse;
import org.apache.olingo.odata2.api.ep.EntityProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SAPPublicCloudService {

    public static final String PC_IMPORT_TOKEN_PATH = "/sap/opu/odata/sap/APS_EXT_ATO_IMP_SRV";
    public static final String SAP_CBC = "/cbc/project-experience";
    public static final String GET_ALL_PC_TR = "/sap/opu/odata/sap/UI_CUSTOMIZING_REQ_M_O2/Requests?sap-client=%s";
    public static final String GET_PC_TR_BY_ID = "/sap/opu/odata/sap/UI_CUSTOMIZING_REQ_M_O2/Requests('%s')?sap-client=%s";
    public static final String GET_ALL_PC_SOFTWARE = "/sap/opu/odata/sap/APS_EXT_ATO_EXP_SRV/CollectionVersionSet?sap-client=%s";
    public static final String GET_PC_SOFTWARE_LATEST_EXPORT_VERSION = "/sap/opu/odata/sap/APS_EXT_ATO_EXP_SRV/CollectionSet('%s')/ExportedCollectionVersions?sap-client=%s";
    public static final String GET_PC_SOFTWARE_ITEMS = "/sap/opu/odata/sap/APS_EXT_ATO_EXP_SRV/ExportedCollectionVersionSet(CollectionId='%s',Version=%s)/Items?sap-client=%s";
    // check Pc Artifact import Paths
    public static final String PC_ARTIFACT_IMPORT = "/sap/opu/odata/sap/APS_EXT_ATO_IMP_SRV/$batch?sap-client=%s";
    public static final String PC_COMMON_IMPORT_FILTER_VERSION = "CommonImportFilteredVersionsToBeImported?sap-client=%s";
    public static final String PC_COMMON_IMPORT_DEPENDENT_VERSION = "CommonImportDependentVersionsToImport?sap-client=%s";
    public static final String PC_COMMON_IMPORT_VALIDATION_MSG = "CommonImportValidationMessages?sap-client=%s";
    public static final String PC_COMMON_IMPORT_SUMMARY = "CommonImportSummary?sap-client=%s";
    public static final String PC_SELECTED_VERSION_IMPORT = "SetSelectedCollectionVersionToBeImported?sap-client=%S&CollectionId='%s'&Version=%s";
    public static final String PC_COMMON_IMPORT_VALIDATE = "CommonImportValidate?sap-client=%s";

    // import Pc Artifact path
    public static final String PC_COMMON_IMPORT_PREPARE = "CommonImportPrepare?sap-client=%s";
    public static final String PC_COMMON_IMPORT = "CommonImport?sap-client=%s";
    public static final String PC_COMMON_IMPORT_ADD_NOTE = "CommonImportAddNote?sap-client=%s&Note='%s'";

    public static final String PC_ARTIFACT_IMPORT_DETAILS = "/sap/opu/odata/sap/APS_EXT_ATO_IMP_SRV/CollectionVersionSet(CollectionId='%s',Version=%s)?sap-client=%s";

    public static final String PC_TR_OBJECTS = "/sap/opu/odata/sap/UI_CUSTOMIZING_REQ_M_O2/Requests('%s')/to_Objects?sap-client=%s&$skip=0&$top=200"; // get latest 200  TR objects only.

    private final ObjectMapper mapper = new ObjectMapper();

    public String getPCCsrfToken(PCConnConfig config, String pathUrl, PCBrowserClient pcBrowserClient) throws URISyntaxException {
        return pcBrowserClient.getCsrfToken(config, pathUrl);
    }

    public String getCBCXsrfToken(PCConnConfig config, CBCBrowserClient cbcBrowserClient) throws URISyntaxException {
        return cbcBrowserClient.getXsrfToken(config);
    }

    public List<CBCWorkSpace> getCBCWorkSpace(PCConnConfig config, CBCBrowserClient cbcBrowserClient) throws JsonProcessingException, URISyntaxException {
        String csrfToken = this.getCBCXsrfToken(config, cbcBrowserClient);
        JsonNode cbcPayloadNode = this.prepareCBCWorkSpacePayLoad();
        List<CBCWorkSpace> cbcWorkSpaceList = new ArrayList<>();
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("sap-xsrf", csrfToken);
        ResponseEntity<String> response = cbcBrowserClient.execute(config, HttpMethod.POST, SAP_CBC, headers, cbcPayloadNode);
        String responseBody = response.getBody();
        if (!ObjectUtils.isEmpty(responseBody)) {
            JsonNode node = mapper.readTree(responseBody);
            if (node.isObject()) {
                JsonNode cbcNode = node.get("data");
                if (cbcNode.isEmpty()) {
                    return cbcWorkSpaceList;
                }
                if (cbcNode.isArray()) {
                    ArrayNode rootArray = (ArrayNode) cbcNode;
                    if (rootArray.isEmpty()) {
                        return cbcWorkSpaceList;
                    }
                    return this.parseCBCWorkSpace(rootArray, cbcWorkSpaceList);
                }
            }
        }
        return cbcWorkSpaceList;
    }

    public CBCTargetSystemDetails getCBCTargetDeploymentDetails(String cbcTargetDeploymentId, String cbcWorkSpaceId, PCConnConfig config, CBCBrowserClient cbcBrowserClient) throws JsonProcessingException, URISyntaxException {
        String csrfToken = this.getCBCXsrfToken(config, cbcBrowserClient);
        JsonNode cbcPayloadNode = this.prepareCBCTargetSystemDetailsPayLoad(cbcTargetDeploymentId, cbcWorkSpaceId);
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("sap-xsrf", csrfToken);
        ResponseEntity<String> response = cbcBrowserClient.execute(config, HttpMethod.POST, SAP_CBC, headers, cbcPayloadNode);
        String responseBody = response.getBody();
        if (!ObjectUtils.isEmpty(responseBody)) {
            JsonNode node = mapper.readTree(responseBody);
            if (node.isObject()) {
                JsonNode cbcNode = node.get("data");
                if (cbcNode.isEmpty()) {
                    return null;
                }
                if (!ObjectUtils.isEmpty(cbcNode.get(0))) {
                    JsonNode pJson = cbcNode.get(0).get("data");
                    if (!ObjectUtils.isEmpty(pJson)) {
                        return this.parseCBCTargetSystemDetails(pJson);
                    }
                }
            }
        }
        return null;
    }


    public List<CBCArtifact> getCBCArtifacts(long projectId, long envId, PCConnConfig config, String cbcProjectId, CBCBrowserClient cbcBrowserClient) throws JsonProcessingException, URISyntaxException {
        JsonNode payloadNode = prepareCBCTaskPayLoad(cbcProjectId);
        List<CBCArtifact> cbcArtifactList = new ArrayList<>();
        String csrfToken = this.getCBCXsrfToken(config, cbcBrowserClient);
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("sap-xsrf", csrfToken);
        ResponseEntity<String> response = cbcBrowserClient.execute(config, HttpMethod.POST, SAP_CBC, headers, payloadNode);
        String responseBody = response.getBody();
        if (!ObjectUtils.isEmpty(responseBody)) {
            JsonNode node = mapper.readTree(responseBody);
            if (node.isObject()) {
                JsonNode pcNode = node.get("data");
                if (pcNode.isEmpty()) {
                    return cbcArtifactList;
                }
                JsonNode resultNode = pcNode.get(0).get("data");
                if (resultNode.isEmpty()) {
                    return cbcArtifactList;
                }
                return this.parseSapCBCArtifact(resultNode, projectId, envId, cbcArtifactList);
            }
        }
        return cbcArtifactList;
    }

    public boolean isAllowedDeployCBArtifact(String workSpaceId, PCConnConfig config, CBCBrowserClient cbcBrowserClient) throws URISyntaxException, JsonProcessingException {
        JsonNode payloadNode = prepareCheckCBCAllowToDeployPayLoad(workSpaceId);
        String csrfToken = this.getCBCXsrfToken(config, cbcBrowserClient);
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("sap-xsrf", csrfToken);
        ResponseEntity<String> response = cbcBrowserClient.execute(config, HttpMethod.POST, SAP_CBC, headers, payloadNode);
        String responseBody = response.getBody();
        JsonNode node = mapper.readTree(responseBody);
        JsonNode dataNode = node.get("data").get(0);
        if (!ObjectUtils.isEmpty(dataNode)) {
            JsonNode pNode = dataNode.get("data");
            return !ObjectUtils.isEmpty(pNode) && "ALLOWED".equalsIgnoreCase(pNode.get("status").asText());
        }
        return false;
    }

    public String deployCBCArtifact(String workSpaceId, String deployDescription, PCConnConfig config, CBCBrowserClient cbcBrowserClient) throws URISyntaxException {
        JsonNode payloadNode = prepareCBCDeployPayLoad(workSpaceId, deployDescription);
        String csrfToken = this.getCBCXsrfToken(config, cbcBrowserClient);
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("sap-xsrf", csrfToken);
        ResponseEntity<String> response = cbcBrowserClient.execute(config, HttpMethod.POST, SAP_CBC, headers, payloadNode);
        return response.getBody();

    }

    public JsonNode getCbcChangeSetLog(Date startDate, Date endDate, String workSpaceId, String projectId, PCConnConfig config, CBCBrowserClient cbcBrowserClient) throws URISyntaxException, JsonProcessingException {
        JsonNode payloadNode = this.prepareAuditLogPayLoad(startDate, endDate, workSpaceId, projectId);
        String csrfToken = this.getCBCXsrfToken(config, cbcBrowserClient);
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("sap-xsrf", csrfToken);
        ResponseEntity<String> response = cbcBrowserClient.execute(config, HttpMethod.POST, SAP_CBC, headers, payloadNode);
        return mapper.readTree(response.getBody());
    }

    public List<JsonNode> deploymentHistoryDetailsForCBCArtifact(String deploymentTargetUUID, PCConnConfig config, CBCBrowserClient cbcBrowserClient ) throws URISyntaxException, JsonProcessingException {
        JsonNode payloadNode = prepareCBCDeploymentHistoryDetailsPayLoad(deploymentTargetUUID);
        String csrfToken = this.getCBCXsrfToken(config, cbcBrowserClient);
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("sap-xsrf", csrfToken);
        ResponseEntity<String> response = cbcBrowserClient.execute(config, HttpMethod.POST, SAP_CBC, headers, payloadNode);
        String responseBody = response.getBody();
        JsonNode cbcProgressNode = mapper.readTree(responseBody);
        if(cbcProgressNode != null) {
            JsonNode rootDataNode = cbcProgressNode.get("data").get(0);
            if(rootDataNode != null) {
                JsonNode dataNode = rootDataNode.get("data");
                if (dataNode.isArray()) {
                    List<JsonNode> nodeList = new ArrayList<>();
                    dataNode.forEach(nodeList::add);
                    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                    nodeList.sort(Comparator.comparing((JsonNode node) -> {
                        String rawDate = node.get("Date").asText();
                        try {
                            return formatter.parse(rawDate);
                        } catch (ParseException e) {
                            throw new RuntimeException(e);
                        }
                    }).reversed());
                    return nodeList;
                }
            }
        }
        return null;

    }

    public RuntimeImportArtifact getCbcDeployProgress(String workSpaceId, PCConnConfig config, CBCBrowserClient cbcBrowserClient) throws URISyntaxException, JsonProcessingException {
        JsonNode payloadNode = prepareCBCDeployProgressPayLoad(workSpaceId);
        String csrfToken = this.getCBCXsrfToken(config, cbcBrowserClient);
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("sap-xsrf", csrfToken);
        ResponseEntity<String> response = cbcBrowserClient.execute(config, HttpMethod.POST, SAP_CBC, headers, payloadNode);
        String responseBody = response.getBody();
        JsonNode cbcProgressNode = mapper.readTree(responseBody);
        if(cbcProgressNode != null) {
            JsonNode dataNode = cbcProgressNode.get("data").get(0);
            if(dataNode != null) {
                return prepareRuntimeDeploymentPercentage(dataNode);
            }
        }
        return null;
    }

    public List<PCArtifact> getPcTrArtifacts(long projectId, long envId, String clientId, PCConnConfig config, PCBrowserClient pcBrowserClient) throws JsonProcessingException {
        List<PCArtifact> pcArtifactList = new ArrayList<>();
        String filter = "&$skip=0&$top=500&$orderby=TransportRequestID%20desc&$filter=TransportRequestStatusOld%20eq%20'R'";
        String path = String.format(GET_ALL_PC_TR, clientId) + filter;
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        ResponseEntity<String> response = pcBrowserClient.execute(config, HttpMethod.GET, path, headers, null);
        String responseBody = response.getBody();
        if (!ObjectUtils.isEmpty(responseBody)) {
            JsonNode node = mapper.readTree(responseBody);
            JsonNode pcNode = node.get("d");
            if (pcNode.isEmpty()) {
                return pcArtifactList;
            }
            JsonNode resultNode = pcNode.get("results");
            if (resultNode.isEmpty()) {
                return pcArtifactList;
            }
            for (JsonNode pJson : resultNode) {
                pcArtifactList.add(this.parsePcTrArtifacts(pJson, projectId, envId));
            }
        }
        return pcArtifactList;
    }

    public PCArtifact getPcTransportById(long projectId, long envId, String trId, String clientId, PCConnConfig config, PCBrowserClient pcBrowserClient) throws JsonProcessingException {
        String path = String.format(GET_PC_TR_BY_ID, trId, clientId);
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        ResponseEntity<String> response = pcBrowserClient.execute(config, HttpMethod.GET, path, headers, null);
        String responseBody = response.getBody();
        if (!ObjectUtils.isEmpty(responseBody)) {
            JsonNode node = mapper.readTree(responseBody);
            JsonNode pcNode = node.get("d");
            if (pcNode.isEmpty()) {
                return null;
            }
           return this.parsePcTrArtifacts(pcNode, projectId, envId);
        }
        return null;
    }

    public JsonNode getPcTransportObject(String artifactId, String clientId, PCConnConfig config, PCBrowserClient pcBrowserClient) throws JsonProcessingException {
        String path = String.format(PC_TR_OBJECTS, artifactId, clientId);
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        ResponseEntity<String> response = pcBrowserClient.execute(config, HttpMethod.GET, path, headers, null);
        String responseBody = response.getBody();
        JsonNode trObjectNode = null;
        if (!ObjectUtils.isEmpty(responseBody)) {
            JsonNode node = mapper.readTree(responseBody);
            JsonNode pcNode = node.get("d");
            if (pcNode.isEmpty()) {
                return null;
            }
            trObjectNode = pcNode.get("results");
        }
        return trObjectNode;
    }

    public PCArtifact getPcSoftwareLatestExportVersion(String artifactId, String clientId, PCConnConfig config, long projectId, long envId, PCBrowserClient pcBrowserClient) throws JsonProcessingException {
        String filter = "&$skip=0&$top=1&$orderby=Version%20desc";
        String path = String.format(GET_PC_SOFTWARE_LATEST_EXPORT_VERSION, artifactId, clientId) + filter;
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        ResponseEntity<String> response = pcBrowserClient.execute(config, HttpMethod.GET, path, headers, null);
        String responseBody = response.getBody();
        if (!ObjectUtils.isEmpty(responseBody)) {
            JsonNode node = mapper.readTree(responseBody);
            JsonNode pcNode = node.get("d");
            if (pcNode.isEmpty()) {
                return null;
            }
            JsonNode resultNode = pcNode.get("results");
            if (resultNode.isEmpty()) {
                return null;
            }
            return this.parsePcSoftwareArtifactList(resultNode.get(0), projectId, envId, clientId, config, pcBrowserClient);
        }
        return null;
    }

    public List<PCSoftwareItemDetails> getPcSoftwareItems(String artifactId, String clientId, String version, PCConnConfig config, PCBrowserClient pcBrowserClient) throws JsonProcessingException {
        String path = String.format(GET_PC_SOFTWARE_ITEMS, artifactId, version, clientId);
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        ResponseEntity<String> response = pcBrowserClient.execute(config, HttpMethod.GET, path, headers, String.class);
        String responseBody = response.getBody();
        if (!ObjectUtils.isEmpty(responseBody)) {
            JsonNode node = mapper.readTree(responseBody);
            JsonNode pcNode = node.get("d");
            if (pcNode.isEmpty()) {
                return null;
            }
            JsonNode resultNode = pcNode.get("results");
            if (resultNode.isEmpty()) {
                return null;
            }
            return this.parsePcSoftwareItems(resultNode);
        }
        return null;
    }

    public List<PCArtifact> getPcSoftWareArtifacts(long projectId, long envId, String clientId, PCConnConfig config, PCBrowserClient pcBrowserClient) throws JsonProcessingException {
        List<PCArtifact> pcArtifactList = new ArrayList<>();
        String filter = "&$filter=Category%20ne%20%27L%27";
        String path = String.format(GET_ALL_PC_SOFTWARE, clientId) + filter;
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        ResponseEntity<String> response = pcBrowserClient.execute(config, HttpMethod.GET, path, headers, null);
        String responseBody = response.getBody();
        if (!ObjectUtils.isEmpty(responseBody)) {
            JsonNode node = mapper.readTree(responseBody);
            JsonNode pcNode = node.get("d");
            if (pcNode.isEmpty()) {
                return pcArtifactList;
            }
            JsonNode resultNode = pcNode.get("results");
            if (resultNode.isEmpty()) {
                return pcArtifactList;
            }
            for (JsonNode pNode : resultNode) {
                String version = pNode.get("Version").asText();
                int pcVersion = Integer.parseInt(version);
                if (pcVersion == 1) {
                    continue;
                }
                String artifactId = pNode.get("CollectionId").asText();
                PCArtifact pcArtifact = getPcSoftwareLatestExportVersion(artifactId, clientId, config, projectId, envId, pcBrowserClient);
                if (pcArtifact != null) {
                    pcArtifactList.add(pcArtifact);
                }
            }
        }
        return pcArtifactList;
    }

    public List<RoODataBatchResponse> validateImportPcArtifact(List<PCArtifact> pcArtifactList, String clientId, PCConnConfig config, PCBrowserClient pcBrowserClient) throws Exception {
        String csrfToken = this.getPCCsrfToken(config, PC_IMPORT_TOKEN_PATH, pcBrowserClient);
        String batchBoundary = "batch-" + UUID.randomUUID();
        String batchRequest = this.generatevalidationPcImportBatchReq(pcArtifactList, clientId, csrfToken, batchBoundary);
        String pathUrl = String.format(PC_ARTIFACT_IMPORT, clientId);
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", String.format("multipart/mixed; boundary=%s", batchBoundary));
        headers.set("X-csrf-token", csrfToken);
        headers.setAccept(List.of(MediaType.MULTIPART_MIXED));
        ResponseEntity<String> response = pcBrowserClient.execute(config, HttpMethod.POST, pathUrl, headers, batchRequest);
        return parseODataResponse(response);
    }

    public List<RoODataBatchResponse> getPcDependentArtifact(List<PCArtifact> pcArtifactList, String clientId, PCConnConfig config, PCBrowserClient pcBrowserClient) throws Exception {
        String csrfToken = this.getPCCsrfToken(config, PC_IMPORT_TOKEN_PATH, pcBrowserClient);
        String batchBoundary = "batch-" + UUID.randomUUID();
        String batchRequest = this.generatePcDependentBatchReq(pcArtifactList, clientId, csrfToken, batchBoundary);
        String pathUrl = String.format(PC_ARTIFACT_IMPORT, clientId);
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", String.format("multipart/mixed; boundary=%s", batchBoundary));
        headers.set("X-csrf-token", csrfToken);
        headers.setAccept(List.of(MediaType.MULTIPART_MIXED));
        ResponseEntity<String> response = pcBrowserClient.execute(config, HttpMethod.POST, pathUrl, headers, batchRequest);
        return parseODataResponse(response);
    }

    public List<RoODataBatchResponse> importAllPcArtifact(List<PCArtifact> pcArtifactList, String clientId, PCConnConfig config, PCBrowserClient pcBrowserClient) throws Exception {
        String csrfToken = this.getPCCsrfToken(config, PC_IMPORT_TOKEN_PATH, pcBrowserClient);
        String batchBoundary = "batch-" + UUID.randomUUID();
        String batchRequest = this.generateAllPcImportBatchReq(pcArtifactList, clientId, csrfToken, batchBoundary);
        String pathUrl = String.format(PC_ARTIFACT_IMPORT, clientId);
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", String.format("multipart/mixed; boundary=%s", batchBoundary));
        headers.set("x-csrf-token", csrfToken);
        ResponseEntity<String> response = pcBrowserClient.execute(config, HttpMethod.POST, pathUrl, headers, batchRequest);
        return parseODataResponse(response);
    }

    public RuntimeImportArtifact getImportPcArtifactDetails(String artifactId, String version, String clientId, PCConnConfig config, PCBrowserClient pcBrowserClient) throws JsonProcessingException {
           String pathUrl = String.format(PC_ARTIFACT_IMPORT_DETAILS, artifactId, version, clientId);
           HttpHeaders headers = new HttpHeaders();
           headers.setAccept(List.of(MediaType.APPLICATION_JSON));
           ResponseEntity<String> response = pcBrowserClient.execute(config, HttpMethod.GET, pathUrl, headers, "");
           JsonNode resNode = mapper.readTree(response.getBody());
           if (resNode != null) {
               JsonNode importNode = resNode.get("d");
               if (importNode != null) {
                   return this.prepareRuntimeImportPCArtifact(importNode);
               }
           }
           return null;
    }

    private PCArtifact parsePcTrArtifacts(JsonNode node, long projectId, long envId) {
        String trReleaseStatus = node.get("TransportRequestStatusOld").asText();
        String description = node.get("TransportRequestDesc").asText();
        String artifactId = node.get("TransportRequestID").asText();
        String changedOn = node.hasNonNull("TransportRequestChangedOn") ? node.get("TransportRequestChangedOn").asText() : null;
        String changedAt = node.get("TransportRequestChangedAt").asText();

        PCArtifact pcArtifact = new PCArtifact();
        pcArtifact.setEnvId(envId);
        pcArtifact.setProjectId(projectId);
        pcArtifact.setArtifactId(artifactId);
        pcArtifact.setName(description);
        pcArtifact.setType(PCArtifactType.TRANSPORT);

        PCTransport pcTr = new PCTransport();
        pcTr.setOnAssignToMeAc(node.get("onAssignToMe_ac").asBoolean(false));
        pcTr.setOnChangeCategoryAc(node.get("onChangeCategory_ac").asBoolean(false));
        pcTr.setOnCopyTransportAc(node.get("onCopyTransport_ac").asBoolean(false));
        pcTr.setOnMergeRequestAc(node.get("OnMergeRequest_ac").asBoolean(false));
        pcTr.setOnRequestCheckAc(node.get("onRequestCheck_ac").asBoolean(false));
        pcTr.setOnRequestReleaseAc(node.get("onRequestRelease_ac").asBoolean(false));
        pcTr.setOnStatusRefreshAc(node.get("onStatusRefresh_ac").asBoolean(false));
        pcTr.setDeleteMc(node.get("Delete_mc").asBoolean(false));
        pcTr.setUpdateMc(node.get("Update_mc").asBoolean(false));
        pcTr.setTransportRequestID(artifactId);
        pcTr.setTransportRequestDesc(description);
        pcTr.setTransportRequestType(node.get("TransportRequestType").asText());
        pcTr.setTransportRequestOwner(node.get("TransportRequestOwner").asText());
        pcTr.setTransportRequestStatusText(node.get("TransportRequestStatusText").asText());
        pcTr.setTransportRequestChangedOn(DateUtil.convertSdsDateTime(changedOn));
        pcTr.setTransportRequestChangedAt(changedAt);
        pcTr.setProjectId(node.get("ProjectID").asText());
        pcTr.setProjectDescription(node.get("ProjectDescription").asText());
        pcTr.setTransportRequestCategoryText(node.get("TransportRequestCategoryText").asText());
        pcTr.setTransportRequestTarget(node.get("TransportRequestTarget").asText());
        pcTr.setLogHandle(node.get("LogHandle").asText());
        pcTr.setEnableLogs(node.get("EnableLogs").asBoolean(false));
        pcTr.setCriticality(node.get("Criticality").asInt(0));
        pcTr.setFieldHidden(node.get("IsFieldHidden").asBoolean(false));
        pcTr.setRepositoryIdHidden(node.get("IsRepositoryIdHidden").asBoolean(false));
        pcTr.setAtoTransportType(node.get("ATOTransportType").asText());
        pcTr.setTransportRequestTypeText(node.get("TransportRequestTypeText").asText());
        pcTr.setUserDescription(node.get("UserDescription").asText());
        pcTr.setTransportRequestStatusOld(trReleaseStatus);
        pcTr.setEnableCBCNavigation(node.get("EnableCBCNavigation").asBoolean(false));
        pcTr.setCbcStagingID(node.get("CBCStagingID").asText());
        pcTr.setRepositoryId(node.get("RepositoryId").asText());
        pcTr.setTransportRequestNotes(node.get("TransportRequestNotes").asText());

        PCArtifactVersion version = new PCArtifactVersion();
        version.setEnvId(envId);
        version.setProjectId(projectId);
        version.setPcArtifactDetails(pcTr);
        version.setVersion("1"); // for Transport Artifact the default version is "1".
        version.setSapChangedOn(DateUtil.convertSdsDateTime(changedOn));
        version.setSapChangedBy(changedAt);
        version.setDescription(description);
        pcArtifact.setPcArtifactVersion(version);
        return pcArtifact;
    }

    private PCArtifact parsePcSoftwareArtifactList(JsonNode node, long projectId, long envId, String clientId, PCConnConfig config, PCBrowserClient pcBrowserClient) throws JsonProcessingException {
        String version = node.get("Version").asText();
        String name = node.get("Description").asText();
        String artifactId = node.get("CollectionId").asText();
        String changedOn = node.hasNonNull("ChangedAt") ? node.get("ChangedAt").asText() : null;
        String changedBy = node.get("ChangedBy").asText();
        String description = node.get("ActionDescription").asText();

        PCArtifact pcArtifact = new PCArtifact();
        pcArtifact.setEnvId(envId);
        pcArtifact.setProjectId(projectId);
        pcArtifact.setArtifactId(artifactId);
        pcArtifact.setName(name);
        pcArtifact.setType(PCArtifactType.SOFTWARE_COLLECTION);

        PCSoftware pcSoftware = new PCSoftware();
        pcSoftware.setCollectionId(artifactId);
        pcSoftware.setVersion(version);
        pcSoftware.setTimestamp(DateUtil.convertSdsDateTime(node.hasNonNull("Timestamp") ? node.get("Timestamp").asText() : null));
        pcSoftware.setDescription(name);
        pcSoftware.setCategory(node.get("Category").asText());
        pcSoftware.setStatus(node.get("Status").asText());
        pcSoftware.setStatusDescription(node.get("StatusDescription").asText());
        pcSoftware.setAction(node.get("Action").asText());
        pcSoftware.setActionDescription(description);
        pcSoftware.setActionVariant(node.get("ActionVariant").asText());
        pcSoftware.setActionStatus(node.get("ActionStatus").asText());
        pcSoftware.setActionStatusDescription(node.get("ActionStatusDescription").asText());
        pcSoftware.setActionText(node.get("ActionText").asText());
        pcSoftware.setChangedBy(changedBy);
        pcSoftware.setChangedByDescription(node.get("ChangedByDescription").asText());
        pcSoftware.setChangedAt(DateUtil.convertSdsDateTime(changedOn));
        pcSoftware.setNoOfVlogs(node.get("NoOfVlogs").asInt());
        pcSoftware.setCurrentBallogHandle(node.get("CurrentBallogHandle").asText());
        pcSoftware.setNoOfCollectionItems(node.get("NoOfExportedItems").asInt());
        pcSoftware.setNoOfVnotes(node.get("NoOfVnotes").asInt());
        pcSoftware.setDevelopmentNamespace(node.get("DevelopmentNamespace").asText());

        pcSoftware.setItemDetails(this.getPcSoftwareItems(artifactId, clientId, version, config, pcBrowserClient));

        PCArtifactVersion pcversion = new PCArtifactVersion();
        pcversion.setEnvId(envId);
        pcversion.setProjectId(projectId);
        pcversion.setPcArtifactDetails(pcSoftware);
        pcversion.setSapChangedOn(DateUtil.convertSdsDateTime(changedOn));
        pcversion.setSapChangedBy(changedBy);
        pcversion.setVersion(version);
        pcversion.setDescription(description);

        pcArtifact.setPcArtifactVersion(pcversion);
        return pcArtifact;
    }

    private List<PCSoftwareItemDetails> parsePcSoftwareItems(JsonNode pJson) {
        List<PCSoftwareItemDetails> itemDetailsList = new ArrayList<>();
        for (JsonNode itemNode : pJson) {
            PCSoftwareItemDetails item = new PCSoftwareItemDetails();
            item.setItemId(itemNode.get("ItemId").asText());
            item.setItemName(itemNode.get("ItemDescription").asText());
            item.setItemType(itemNode.get("ItemTypeText").asText());
            item.setItemStatus(itemNode.get("ItemStatus").asText());
            item.setItemStatusDescription(itemNode.get("ItemStatusDescription").asText());
            item.setItemChangedBy(itemNode.get("ChangedBy").asText());
            item.setItemChangedByDescription(itemNode.get("ChangedByDescription").asText());
            item.setItemChangedOn(itemNode.hasNonNull("ChangedAt") ? DateUtil.convertSdsDateTime(itemNode.get("ChangedAt").asText()) : null);
            itemDetailsList.add(item);
        }
        return itemDetailsList;
    }

    private List<CBCWorkSpace> parseCBCWorkSpace(ArrayNode pJson, List<CBCWorkSpace> cbcWorkSpaceList) {
        Map<String, CBCWorkSpace> workSpaceMap = new HashMap<>();
        JsonNode projectNode = pJson.get(0).get("data");
        JsonNode workSpaceNode = pJson.get(1).get("data");
        for (JsonNode wNode : workSpaceNode) {
            if ("MW".equalsIgnoreCase(wNode.get("WorkspaceType").asText())) {
                CBCWorkSpace cbcWorkSpace = new CBCWorkSpace();
                String workSpaceId = wNode.get("NodeID").asText();
                cbcWorkSpace.setWorkSpaceId(workSpaceId);
                cbcWorkSpace.setWorkSpaceType(wNode.get("Title").asText());
                cbcWorkSpace.setDeployTargetId(wNode.get("DeployTargetUUID").asText());
                workSpaceMap.put(workSpaceId, cbcWorkSpace);
            }
        }

        for (JsonNode pNode : projectNode) {
            CBCWorkSpace workSpace = workSpaceMap.get(pNode.get("WorkspaceUUID").asText());
            if (workSpace != null) {
                if ("ACTIVE".equalsIgnoreCase(pNode.get("ProjectStatus").asText())) {
                    workSpace.setCbcProjectId(pNode.get("NodeID").asText());
                    cbcWorkSpaceList.add(workSpace);
                }
            }
        }
        return cbcWorkSpaceList;
    }

    private CBCTargetSystemDetails parseCBCTargetSystemDetails(JsonNode pJson) {

        CBCTargetSystemDetails details = new CBCTargetSystemDetails();
        details.setName(pJson.get("name").asText());
        details.setCbcTenantId(pJson.get("tenantId").asText());
        details.setUuId(pJson.get("uuid").asText());
        details.setOperationStatus(pJson.get("operationStatus").asText());
        details.setType(pJson.get("type").asText());
        details.setDemoDataIncluded(pJson.get("isDemoDataIncluded").asBoolean());
        String cbcTenantUrl = "https://" + pJson.get("webGuiUrl").asText();
        details.setCbcTenantUrl(cbcTenantUrl);
        return details;
    }

    private List<CBCArtifact> parseSapCBCArtifact(JsonNode pJson, long projectId, long envId, List<CBCArtifact> cbcArtifactList) {
        for (JsonNode node : pJson) {
            if ("BO_X4_MS_EXPLORE".equalsIgnoreCase(node.get("ElementID").asText())) {
                String status = node.get("Status").asText();
                String name = node.get("Title").asText();
                CBCArtifact cbcArtifact = new CBCArtifact();
                cbcArtifact.setProjectId(projectId);
                cbcArtifact.setEnvId(envId);
                cbcArtifact.setName(name);

                CBCArtifactVersion version = new CBCArtifactVersion();
                version.setProjectId(projectId);
                version.setVersion("1");
                version.setCbcEnvId(envId);
                version.setUuid(node.get("UUID").asText());
                version.setElementId(node.get("ElementID").asText());
                version.setStatus(status);
                version.setDescription(node.get("Description").asText());
                version.setDetailedDescription(node.get("DetailedDescription").asText());

                version.setDueDate(node.hasNonNull("DueDate") ? DateUtil.convertCBCStringToDate(node.get("DueDate").asText()) :  null);
                version.setStartDate(node.hasNonNull("StartDate") ? DateUtil.convertCBCStringToDate(node.get("StartDate").asText()) : null);
                version.setTimeStamp(node.hasNonNull("TimeStamp") ? DateUtil.convertCBCStringToDate(node.get("TimeStamp").asText()) : null);

                version.setAssigneeUUID(node.get("AssigneeUUID").asText());
                version.setTitle(name);
                version.setParent(node.get("Parent").asText());
                version.setActualParent(node.get("ActualParent").asText());
                version.setActivityGroupId(node.get("ActivityGroupID").asText());
                version.setGoLiveRelevantCode(node.get("GoLiveRelevantCode").asText());
                version.setMilestone(node.get("Milestone").asBoolean(false));
                version.setPhase(node.get("Phase").asText());
                version.setSequence(node.get("Sequence").asInt(0));
                version.setType(node.get("Type").asText());
                version.setBusinessArea(node.get("BusinessArea").asText());
                cbcArtifact.setCbcArtifactVersion(version);
                cbcArtifactList.add(cbcArtifact);
            }
        }
        return cbcArtifactList;
    }

    public String generatevalidationPcImportBatchReq(List<PCArtifact> pcArtifactList, String clientId, String csrfToken, String batchBoundary) throws URISyntaxException {
        HttpHeaders queryPartHeaders = new HttpHeaders();
        queryPartHeaders.setContentType(MediaType.APPLICATION_JSON);
        queryPartHeaders.setAccept(List.of(MediaType.APPLICATION_JSON));
        queryPartHeaders.set("X-csrf-token", csrfToken);

        List<BatchPart> batchParts = new ArrayList<>();
        BatchChangeSet changeSet = BatchChangeSet.newBuilder().build();

        // Common Import Filtered Versions To BeImported
        BatchChangeSetPart changeRequestFiltered = BatchChangeSetPart
                .method(HttpMethod.POST.name())
                .uri(String.format(PC_COMMON_IMPORT_FILTER_VERSION, clientId))
                .headers(queryPartHeaders.toSingleValueMap())
                .build();
        changeSet.add(changeRequestFiltered);

        // Common Import Dependent Versions To Import
        BatchChangeSetPart changeRequestDependent = BatchChangeSetPart
                .method(HttpMethod.POST.name())
                .uri(String.format(PC_COMMON_IMPORT_DEPENDENT_VERSION, clientId))
                .headers(queryPartHeaders.toSingleValueMap())
                .build();
        changeSet.add(changeRequestDependent);

        // Common Import Validation Messages
        BatchChangeSetPart changeReqValidationMsg = BatchChangeSetPart
                .method(HttpMethod.POST.name())
                .uri(String.format(PC_COMMON_IMPORT_VALIDATION_MSG, clientId))
                .headers(queryPartHeaders.toSingleValueMap())
                .build();
        changeSet.add(changeReqValidationMsg);

        // Common Import Summary
        BatchChangeSetPart changeReqImportSummary = BatchChangeSetPart
                .method(HttpMethod.POST.name())
                .uri(String.format(PC_COMMON_IMPORT_SUMMARY, clientId))
                .headers(queryPartHeaders.toSingleValueMap())
                .build();
        changeSet.add(changeReqImportSummary);

        for(PCArtifact pcArtifact : pcArtifactList) {
//            String version = PCArtifactType.TRANSPORT == pcArtifact.getType() ? "1" : pcArtifact.getPcArtifactVersion().getVersion();
            //Set Selected Collection Version To BeImported
            BatchChangeSetPart changeReqSelectVersion = BatchChangeSetPart
                    .method(HttpMethod.POST.name())
                    .uri(String.format(PC_SELECTED_VERSION_IMPORT, clientId, pcArtifact.getArtifactId(), pcArtifact.getPcArtifactVersion().getVersion()))
                    .headers(queryPartHeaders.toSingleValueMap())
                    .build();
            changeSet.add(changeReqSelectVersion);
        }

        // Common Import Validate
        BatchChangeSetPart changeReqCommonImportValidate = BatchChangeSetPart
                .method(HttpMethod.POST.name())
                .uri(String.format(PC_COMMON_IMPORT_VALIDATE, clientId))
                .headers(queryPartHeaders.toSingleValueMap())
                .build();
        changeSet.add(changeReqCommonImportValidate);

        if (!changeSet.getChangeSetParts().isEmpty()) {
            batchParts.add(changeSet);
            InputStream body = EntityProvider.writeBatchRequest(batchParts, batchBoundary);
            return new BufferedReader(
                    new InputStreamReader(body, StandardCharsets.UTF_8))
                    .lines().filter(s -> !s.startsWith("Content-Length"))
                    .collect(Collectors.joining("\r\n"));
        }
        return null;
    }


    public String generatePcDependentBatchReq(List<PCArtifact> pcArtifactList, String clientId, String csrfToken, String batchBoundary) throws URISyntaxException {
        HttpHeaders queryPartHeaders = new HttpHeaders();
        queryPartHeaders.setContentType(MediaType.APPLICATION_JSON);
        queryPartHeaders.setAccept(List.of(MediaType.APPLICATION_JSON));
        queryPartHeaders.set("X-csrf-token", csrfToken);

        List<BatchPart> batchParts = new ArrayList<>();
        BatchChangeSet changeSet = BatchChangeSet.newBuilder().build();

        // Common Import Dependent Versions To Import
        BatchChangeSetPart changeRequestDependent = BatchChangeSetPart
                .method(HttpMethod.POST.name())
                .uri(String.format(PC_COMMON_IMPORT_DEPENDENT_VERSION, clientId))
                .headers(queryPartHeaders.toSingleValueMap())
                .build();
        changeSet.add(changeRequestDependent);

        for(PCArtifact pcArtifact : pcArtifactList) {
//            String version = PCArtifactType.TRANSPORT == pcArtifact.getType() ? "1" : pcArtifact.getPcArtifactVersion().getVersion();
            //Set Selected Collection Version To BeImported
            BatchChangeSetPart changeReqSelectVersion = BatchChangeSetPart
                    .method(HttpMethod.POST.name())
                    .uri(String.format(PC_SELECTED_VERSION_IMPORT, clientId, pcArtifact.getArtifactId(), pcArtifact.getPcArtifactVersion().getVersion()))
                    .headers(queryPartHeaders.toSingleValueMap())
                    .build();
            changeSet.add(changeReqSelectVersion);
        }

        // Common Import Validate
        BatchChangeSetPart changeReqCommonImportValidate = BatchChangeSetPart
                .method(HttpMethod.POST.name())
                .uri(String.format(PC_COMMON_IMPORT_VALIDATE, clientId))
                .headers(queryPartHeaders.toSingleValueMap())
                .build();
        changeSet.add(changeReqCommonImportValidate);

        if (!changeSet.getChangeSetParts().isEmpty()) {
            batchParts.add(changeSet);
            InputStream body = EntityProvider.writeBatchRequest(batchParts, batchBoundary);
            return new BufferedReader(
                    new InputStreamReader(body, StandardCharsets.UTF_8))
                    .lines().filter(s -> !s.startsWith("Content-Length"))
                    .collect(Collectors.joining("\r\n"));
        }
        return null;
    }


    public String generateAllPcImportBatchReq(List<PCArtifact> pcArtifactList, String clientId, String csrfToken, String batchBoundary) throws URISyntaxException {
        HttpHeaders queryPartHeaders = new HttpHeaders();
        queryPartHeaders.setContentType(MediaType.APPLICATION_JSON);
        queryPartHeaders.setAccept(List.of(MediaType.APPLICATION_JSON));
        queryPartHeaders.set("x-csrf-token", csrfToken);
        List<BatchPart> batchParts = new ArrayList<>();
        BatchChangeSet changeSet = BatchChangeSet.newBuilder().build();

        //Set Selected Collection Version To BeImported
        for(PCArtifact art : pcArtifactList) {
            BatchChangeSetPart changeReqSelectVersion = BatchChangeSetPart
                    .method(HttpMethod.POST.name())
                    .uri(String.format(PC_SELECTED_VERSION_IMPORT, clientId, art.getArtifactId(), art.getPcArtifactVersion().getVersion()))
                    .headers(queryPartHeaders.toSingleValueMap())
                    .build();
            changeSet.add(changeReqSelectVersion);
        }

        // Common Import Prepare
        BatchChangeSetPart changeReqCommonImportPrepare = BatchChangeSetPart
                .method(HttpMethod.POST.name())
                .uri(String.format(PC_COMMON_IMPORT_PREPARE, clientId))
                .headers(queryPartHeaders.toSingleValueMap())
                .build();
        changeSet.add(changeReqCommonImportPrepare);

        // Common Import
        BatchChangeSetPart changeReqCommonImport = BatchChangeSetPart
                .method(HttpMethod.POST.name())
                .uri(String.format(PC_COMMON_IMPORT, clientId))
                .headers(queryPartHeaders.toSingleValueMap())
                .build();
        changeSet.add(changeReqCommonImport);

        // Common Import Add Note
        BatchChangeSetPart changeReqCommonImportAddNode = BatchChangeSetPart
                .method(HttpMethod.POST.name())
                .uri(String.format(PC_COMMON_IMPORT_ADD_NOTE, clientId, ""))
                .headers(queryPartHeaders.toSingleValueMap())
                .build();
        changeSet.add(changeReqCommonImportAddNode);

        if (!changeSet.getChangeSetParts().isEmpty()) {
            batchParts.add(changeSet);
            InputStream body = EntityProvider.writeBatchRequest(batchParts, batchBoundary);
            return new BufferedReader(
                    new InputStreamReader(body, StandardCharsets.UTF_8))
                    .lines().filter(s -> !s.startsWith("Content-Length"))
                    .collect(Collectors.joining("\r\n"));
        }
        return null;
    }


    public List<RoODataBatchResponse> parseODataResponse(ResponseEntity<String> responseEntity) throws Exception {
        String contentType = responseEntity.getHeaders().getContentType().toString();
        String responseBody = responseEntity.getBody();
        InputStream responseStream = new ByteArrayInputStream(responseBody.getBytes(StandardCharsets.UTF_8));
        List<BatchSingleResponse> responseParts = EntityProvider.parseBatchResponse(responseStream, contentType);
        List<RoODataBatchResponse> oDataREsList = new ArrayList<>();
        for (BatchSingleResponse singleResponse : responseParts) {
            JsonNode body = mapper.readTree(singleResponse.getBody());
            RoODataBatchResponse res = new RoODataBatchResponse();
            res.setStatusCode(Integer.parseInt(singleResponse.getStatusCode()));
            res.setJsonBody(body);
            oDataREsList.add(res);
        }
        return oDataREsList;
    }

    private JsonNode prepareCBCWorkSpacePayLoad() {
        // prepare user project payload
        CBCActionDetails pAction = new CBCActionDetails();
        pAction.setAction(CBCActionType.USER_PROJECTS);
        ObjectNode pNode = pAction.getData();
        List<String> pAttributeList = Arrays.asList("ProjectStatus", "UUID", "WorkspaceUUID", "Type");
        ArrayNode arrayNode = pNode.putArray("requestedAttributes");
        pAttributeList.forEach(arrayNode::add);
        pAction.setOrder(0);

        List<CBCActionDetails> pActionList = new ArrayList<>();
        pActionList.add(pAction);

        CBCAction project = new CBCAction();
        project.setActions(pActionList);

        //prepare user work space payload
        CBCActionDetails wAction = new CBCActionDetails();
        wAction.setAction(CBCActionType.USER_WORKSPACE);
        wAction.setOrder(1);

        List<CBCActionDetails> wActionList = new ArrayList<>();
        wActionList.add(wAction);

        CBCAction workSpace = new CBCAction();
        workSpace.setActions(wActionList);

        CBCPayLoadObj cbcPayLoadObj = new CBCPayLoadObj();
        cbcPayLoadObj.setProject(project);
        cbcPayLoadObj.setWorkspace(workSpace);
        ObjectMapper mapper = new ObjectMapper();
        return mapper.valueToTree(cbcPayLoadObj);
    }

    public JsonNode prepareCBCTargetSystemDetailsPayLoad(String cbcDeploymentTargetId, String cbcWorkSpaceId) {

        //prepare payload work space for getting target deployment details
        CBCActionDetails wAction = new CBCActionDetails();
        wAction.setAction(CBCActionType.TARGET_DEPLOYMENT_DETAILS);
        wAction.setOrder(0);
        ObjectNode pNode = wAction.getData();
        pNode.put("deploymentTargetUUID", cbcDeploymentTargetId);
        pNode.put("workspaceUUID", cbcWorkSpaceId);

        List<CBCActionDetails> wActionList = new ArrayList<>();
        wActionList.add(wAction);

        CBCAction workSpace = new CBCAction();
        workSpace.setActions(wActionList);

        CBCPayLoadObj cbcPayLoadObj = new CBCPayLoadObj();
        cbcPayLoadObj.setWorkspace(workSpace);
        ObjectMapper mapper = new ObjectMapper();
        return mapper.valueToTree(cbcPayLoadObj);

    }

    private JsonNode prepareCBCTaskPayLoad(String cbcProjectId) {
        CBCActionDetails pAction = new CBCActionDetails();
        pAction.setAction(CBCActionType.TASK);
        ObjectNode pNode = pAction.getData();
        pNode.put("excludeConfigurationActivities", true);
        pNode.put("fillTreeStructure", false);
        pNode.put("getAll", true);
        pNode.put("projectId", cbcProjectId);
        pAction.setOrder(0);

        List<CBCActionDetails> pActionList = new ArrayList<>();
        pActionList.add(pAction);

        CBCAction project = new CBCAction();
        project.setActions(pActionList);
        CBCPayLoadObj cbcPayLoadObj = new CBCPayLoadObj();
        cbcPayLoadObj.setProject(project);
        return mapper.valueToTree(cbcPayLoadObj);
    }

    private JsonNode prepareCheckCBCAllowToDeployPayLoad(String workSpaceId) {
        CBCPayLoadObj workSpacePayLoadObj = new CBCPayLoadObj();
        CBCAction action = new CBCAction();
        List<CBCActionDetails> actionDetailsList = new ArrayList<>();
        CBCActionDetails details = new CBCActionDetails();
        details.setAction(CBCActionType.CHECK_ALLOW_DEPLOY);
        ObjectNode data = details.getData();
        data.put("workspaceId", workSpaceId);
        details.setOrder(0);
        actionDetailsList.add(details);
        action.setActions(actionDetailsList);
        workSpacePayLoadObj.setWorkspace(action);
        return mapper.valueToTree(workSpacePayLoadObj);
    }

    private JsonNode prepareCBCDeployPayLoad(String workSpaceId, String deployDescription) {
        CBCPayLoadObj projectPayLoadObj = new CBCPayLoadObj();
        CBCAction action = new CBCAction();
        List<CBCActionDetails> actionDetailsList = new ArrayList<>();
        CBCActionDetails details = new CBCActionDetails();
        details.setAction(CBCActionType.CBC_DEPLOY);
        ObjectNode data = details.getData();
        data.put("workspaceId", workSpaceId);
        data.put("deployDescription", deployDescription);
        data.put("workspaceStatus", "DeployInitiated");
        details.setOrder(0);
        actionDetailsList.add(details);
        action.setActions(actionDetailsList);
        projectPayLoadObj.setProject(action);
        return mapper.valueToTree(projectPayLoadObj);
    }

    private JsonNode prepareCBCDeploymentHistoryDetailsPayLoad(String deploymentTargetUUID) {
        CBCPayLoadObj projectPayLoadObj = new CBCPayLoadObj();
        CBCAction action = new CBCAction();
        List<CBCActionDetails> actionDetailsList = new ArrayList<>();
        CBCActionDetails details = new CBCActionDetails();
        details.setAction(CBCActionType.CBC_DEPLOYMENT_ACTION);
        ObjectNode data = details.getData();
        data.put("deploymentTargetUUID", deploymentTargetUUID);
        details.setOrder(0);
        actionDetailsList.add(details);
        action.setActions(actionDetailsList);
        projectPayLoadObj.setWorkspace(action);
        return mapper.valueToTree(projectPayLoadObj);
    }

    private JsonNode prepareCBCDeployProgressPayLoad(String workSpaceId) {
        CBCPayLoadObj workSpacePayLoadObj = new CBCPayLoadObj();
        CBCAction action = new CBCAction();
        List<CBCActionDetails> actionDetailsList = new ArrayList<>();
        CBCActionDetails details = new CBCActionDetails();
        details.setAction(CBCActionType.CBC_DEPLOYMENT_PROGRESS);
        ObjectNode data = details.getData();
        data.put("workspaceId", workSpaceId);
        details.setOrder(0);
        actionDetailsList.add(details);
        action.setActions(actionDetailsList);
        workSpacePayLoadObj.setWorkspace(action);
        return mapper.valueToTree(workSpacePayLoadObj);
    }

    private RuntimeImportArtifact prepareRuntimeImportPCArtifact(JsonNode pJson) {
        RuntimeImportArtifact importArtifact = new RuntimeImportArtifact();
        importArtifact.setArtifactId(pJson.get("CollectionId").asText());
//        importArtifact.setName(pJson.get("CollectionId").asText());
        importArtifact.setVersion(pJson.get("Version").asText());
        importArtifact.setImportStatus(pJson.get("Status").asText());
        importArtifact.setImportStatusDesc(pJson.get("ImportStatusDescription").asText());
        importArtifact.setImportedBy(pJson.get("ForwardStatusChangedBy").asText());
        importArtifact.setImportedOn(pJson.hasNonNull("ImportedAt") ? DateUtil.convertSdsDateTime(pJson.get("ImportedAt").asText()) : null);
        return importArtifact;
    }

    private RuntimeImportArtifact prepareRuntimeDeploymentPercentage(JsonNode pJson) {
        RuntimeImportArtifact importArtifact = new RuntimeImportArtifact();
        importArtifact.setCbcDeploymentPercentage(pJson.get("data").get("percentage").asInt());
        importArtifact.setCbcDeployProcessTime(pJson.get("processTime").asText());
        return importArtifact;
    }

    private JsonNode prepareAuditLogPayLoad(Date startDate, Date endDate, String workSpaceId, String cbcProjectId) {
        CBCPayLoadObj project = new CBCPayLoadObj();
        CBCAction action = new CBCAction();
        List<CBCActionDetails> actionDetailsList = new ArrayList<>();
        CBCActionDetails details = new CBCActionDetails();
        details.setAction(CBCActionType.CBC_AUDIT_LOG);

        ObjectNode data = details.getData();
        data.put("startTime", startDate.getTime());
        data.put("endTime", endDate.getTime());
        data.put("pageNumber", 0);
        data.put("numberOfItemsPerPage", 500);

        ArrayNode areaNode = mapper.createArrayNode();
        data.set("areaId", areaNode);

        ArrayNode identidyUUIDsNode = mapper.createArrayNode();
        data.set("identidyUUIDs", identidyUUIDsNode);

        ArrayNode projectNode = mapper.createArrayNode();
        projectNode.add(String.format("%s:%s", workSpaceId, cbcProjectId));
        data.set("projectId", projectNode);

        ObjectNode sortOptionNode = mapper.createObjectNode();
        sortOptionNode.put("element", "ChangedOn");
        sortOptionNode.put("ascending", false);
        data.set("sortOptions", sortOptionNode);

        details.setOrder(0);
        actionDetailsList.add(details);
        action.setActions(actionDetailsList);
        project.setProject(action);
        return mapper.valueToTree(project);

    }

}
