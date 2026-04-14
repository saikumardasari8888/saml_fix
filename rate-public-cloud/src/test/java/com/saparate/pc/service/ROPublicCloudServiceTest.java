package com.saparate.pc.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import tools.jackson.databind.ObjectMapper;
import com.rate.user.entity.Customer;
import com.saparate.pc.entity.PCArtifact;
import com.saparate.pc.entity.PCArtifactVersion;
import com.saparate.pc.exception.ROPublicCloudException;
import com.saparate.pc.model.PCArtifactType;
import com.saparate.pc.model.PCValidationReport;
import com.saparate.pc.repository.PCEnvironmentRepo;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@SpringBootTest(classes = {ROPublicCloudService.class, PCEnvironmentRepo.class})
@ExtendWith(SpringExtension.class)
class ROPublicCloudServiceTest {

    @Autowired
    private ROPublicCloudService roPublicCloudService;
//    private ROPublicCloudService roPublicCloudService = new ROPublicCloudService();

    ObjectMapper mapper = new ObjectMapper();

    private List<PCArtifact> preparePCArt() {
        List<PCArtifact> pcArtifactList = new ArrayList<>();

/*        PCArtifact art1 = new PCArtifact();
        art1.setType(PCArtifactType.TRANSPORT);
        art1.setArtifactId("AVTK900176");
        PCArtifactVersion pcVer1 = new PCArtifactVersion();
        pcVer1.setVersion("1");
        art1.setPcArtifactVersion(pcVer1);
        pcArtifactList.add(art1);*/

        PCArtifact art2 = new PCArtifact();
        art2.setType(PCArtifactType.TRANSPORT);
        art2.setArtifactId("AVTK900176");
        PCArtifactVersion pcVer2 = new PCArtifactVersion();
        pcVer2.setVersion("1");
        art2.setPcArtifactVersion(pcVer2);
        pcArtifactList.add(art2);

/*        PCArtifact art3 = new PCArtifact();
        art3.setType(PCArtifactType.SOFTWARE_COLLECTION);
        art3.setArtifactId("YY1_2");
        PCArtifactVersion pcVer3 = new PCArtifactVersion();
        pcVer3.setVersion("5");
        art3.setPcArtifactVersion(pcVer3);
        pcArtifactList.add(art3);*/


        return pcArtifactList;

    }
    @Test
    void validatePcArtifacts() throws ROPublicCloudException, JsonProcessingException {
        Customer userObj = Customer.createUserObj("basha.s@releaseowl.com", "rolocal");
        PCValidationReport resNode = roPublicCloudService.validatePcArtifacts(preparePCArt(), userObj, 2, 2);
        System.out.println(mapper.writeValueAsString(resNode));
    }
}