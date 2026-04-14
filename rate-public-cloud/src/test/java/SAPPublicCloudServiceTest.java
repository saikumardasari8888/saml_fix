import com.fasterxml.jackson.core.JsonProcessingException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.saparate.pc.client.CBCBrowserClient;
import com.saparate.pc.client.PCBrowserClient;
import com.saparate.pc.config.PCConnConfig;
import com.saparate.pc.entity.PCArtifact;
import com.saparate.pc.entity.PCArtifactVersion;
import com.saparate.pc.model.PCArtifactType;
import com.saparate.pc.model.RoODataBatchResponse;
import com.saparate.pc.model.RuntimeImportArtifact;
import com.saparate.pc.service.ROPublicCloudService;
import com.saparate.pc.service.SAPPublicCloudService;
import org.apache.commons.lang3.ObjectUtils;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class SAPPublicCloudServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ROPublicCloudService publicCloudService = new ROPublicCloudService();

    private List<PCArtifact> preparePCArt() {
        List<PCArtifact> pcArtifactList = new ArrayList<>();

        PCArtifact art1 = new PCArtifact();
        art1.setType(PCArtifactType.TRANSPORT);
        art1.setArtifactId("AVTK900176");
        PCArtifactVersion pcVer1 = new PCArtifactVersion();
        pcVer1.setVersion("1");
        art1.setPcArtifactVersion(pcVer1);
        pcArtifactList.add(art1);

        PCArtifact art2 = new PCArtifact();
        art2.setType(PCArtifactType.TRANSPORT);
        art2.setArtifactId("AVTK900192");
        PCArtifactVersion pcVer2 = new PCArtifactVersion();
        pcVer2.setVersion("1");
        art2.setPcArtifactVersion(pcVer2);
        pcArtifactList.add(art2);

        PCArtifact art3 = new PCArtifact();
        art3.setType(PCArtifactType.SOFTWARE_COLLECTION);
        art3.setArtifactId("YY1_2");
        PCArtifactVersion pcVer3 = new PCArtifactVersion();
        pcVer3.setVersion("5");
        art3.setPcArtifactVersion(pcVer3);
        pcArtifactList.add(art3);


        return pcArtifactList;

    }

    @Test
    void validateImportPcArtifactTest() throws Exception {
        SAPPublicCloudService service = new SAPPublicCloudService();
        PCBrowserClient pcBrowserClient = new PCBrowserClient();
        PCConnConfig config = new PCConnConfig("https://my427547.s4hana.cloud.sap", "basha.s@releaseowl.com", "Basha123#");
        List<RoODataBatchResponse> oDataRes = service.validateImportPcArtifact(preparePCArt(), "100", config ,pcBrowserClient);
        Map<String, List<JsonNode>> map = publicCloudService.parsePcValidationMap(oDataRes);
/*        System.out.println("[");
        for(RoODataBatchResponse res : oDataRes) {
            System.out.println(objectMapper.writeValueAsString(res) + ",");
        }
        System.out.println("]");*/

        System.out.println(objectMapper.writeValueAsString(map));
    }
    @Test
    void getPcDependentArtifactTest() throws Exception {
        SAPPublicCloudService service = new SAPPublicCloudService();
        PCBrowserClient pcBrowserClient = new PCBrowserClient();
        PCConnConfig config = new PCConnConfig("https://my427547.s4hana.cloud.sap", "basha.s@releaseowl.com", "Basha123#");
        List<RoODataBatchResponse> oDataRes = service.getPcDependentArtifact(preparePCArt(), "100", config ,pcBrowserClient);
        Map<String, List<JsonNode>> map = publicCloudService.parsePcValidationMap(oDataRes);
/*        System.out.println("[");
        for(RoODataBatchResponse res : oDataRes) {
            System.out.println(objectMapper.writeValueAsString(res) + ",");
        }
        System.out.println("]");*/

        System.out.println(objectMapper.writeValueAsString(map));
    }

    @Test
    void importPcArtifactTest() throws Exception {
        SAPPublicCloudService service = new SAPPublicCloudService();
        PCBrowserClient pcBrowserClient = new PCBrowserClient();
        PCConnConfig config = new PCConnConfig("https://my427547.s4hana.cloud.sap", "basha.s@releaseowl.com", "Basha123#");
        List<RoODataBatchResponse> oDataRes = service.importAllPcArtifact(preparePCArt(), "100", config ,pcBrowserClient);
        System.out.println("[");
        for(RoODataBatchResponse res : oDataRes) {
            System.out.println(objectMapper.writeValueAsString(res));
            System.out.println(",");
        }
        System.out.println("]");

    }

    @Test
    void getImportPcArtifactDetailsTest() throws JsonProcessingException {
        SAPPublicCloudService service = new SAPPublicCloudService();
        PCBrowserClient pcBrowserClient = new PCBrowserClient();
        PCConnConfig config = new PCConnConfig("https://my427547.s4hana.cloud.sap", "basha.s@releaseowl.com", "Basha123#");
        RuntimeImportArtifact importDetails = service.getImportPcArtifactDetails("YY1_3", "13","100", config, pcBrowserClient);
        System.out.println(importDetails);
    }

    @Test
    void checkDeployAllowed() throws URISyntaxException, JsonProcessingException {
        SAPPublicCloudService service = new SAPPublicCloudService();
        CBCBrowserClient client = new CBCBrowserClient();
        PCConnConfig config = new PCConnConfig("https://my83660018.prod04.cbc.ap.one.cloud.sap", "rahul.p@releaseowl.com", "Pippalla#301");
        boolean status = service.isAllowedDeployCBArtifact("01D2DB62964E4706AED5535DC8C684A1", config, client);
        System.out.println(status);
    }

    @Test
    void deployCBC() throws URISyntaxException, JsonProcessingException {
        SAPPublicCloudService service = new SAPPublicCloudService();
        CBCBrowserClient client = new CBCBrowserClient();
        PCConnConfig config = new PCConnConfig("https://my83660018.prod04.cbc.ap.one.cloud.sap", "rahul.p@releaseowl.com", "Pippalla#301");
        String res =  service.deployCBCArtifact("01D2DB62964E4706AED5535DC8C684A1", "", config, client);
        JsonNode node = objectMapper.readTree(res);
        System.out.println(node);
    }

    @Test
    void cbcDeployProgress() throws URISyntaxException, JsonProcessingException {
        SAPPublicCloudService service = new SAPPublicCloudService();
        CBCBrowserClient client = new CBCBrowserClient();
        PCConnConfig config = new PCConnConfig("https://my83660018.prod04.cbc.ap.one.cloud.sap", "rahul.p@releaseowl.com", "Pippalla#301");
        RuntimeImportArtifact res =  service.getCbcDeployProgress("01D2DB62964E4706AED5535DC8C684A1", config, client);
        System.out.println(res);
    }

    @Test
    void cbcAuditLogDetails() throws URISyntaxException, JsonProcessingException, ParseException {
        SAPPublicCloudService service = new SAPPublicCloudService();
        CBCBrowserClient client = new CBCBrowserClient();
/*        Date startDate = new Date(1753727400000L);
        Date endDate = new Date(1753813799999L);*/
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        Date startDate = formatter.parse("2025-07-29 20:46:26");
        Date endDate = formatter.parse("2025-07-29 21:14:33");
        PCConnConfig config = new PCConnConfig("https://my83660018.prod04.cbc.ap.one.cloud.sap", "rahul.p@releaseowl.com", "Pippalla#301");
        JsonNode res =  service.getCbcChangeSetLog(startDate, endDate,"01D2DB62964E4706AED5535DC8C684A1", "9EEC7C0447E046DA81E6F74117DAC98C", config, client);
        System.out.println(res);
    }

    @Test
    void deploymentTargetDetailsForCBCArtifact() throws URISyntaxException, JsonProcessingException {
        SAPPublicCloudService service = new SAPPublicCloudService();
        CBCBrowserClient client = new CBCBrowserClient();
        PCConnConfig config = new PCConnConfig("https://my83660018.prod04.cbc.ap.one.cloud.sap", "rahul.p@releaseowl.com", "Pippalla#301");
       List<JsonNode> res=  service.deploymentHistoryDetailsForCBCArtifact("39EF7460E56C4B25B2313663BA975F3F", config, client);
        System.out.println(res);
    }

    @Test
    void test() {
        List<String> s1 = Arrays.asList("a", "b", "c", "d");
        List<String> s2 = new ArrayList<>(s1);
        for(int i = 0; i<3 && !ObjectUtils.isEmpty(s2); i++) {
            s2.removeIf("a"::equalsIgnoreCase);
        }
    }

    @Test
    void test1() {
        List<String> s1 = Arrays.asList("a", "b", "c", "d");
        List<String> s2 = new ArrayList<>(s1);
        for(int i = 0; i<3 && !ObjectUtils.isEmpty(s2); i++) {
            s2.removeIf("a"::equalsIgnoreCase);
        }
    }

}
