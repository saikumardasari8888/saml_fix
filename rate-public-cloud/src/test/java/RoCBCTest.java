import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.saparate.pc.enums.CBCActionType;
import com.saparate.pc.model.CBCAction;
import com.saparate.pc.model.CBCActionDetails;
import com.saparate.pc.model.CBCPayLoadObj;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class RoCBCTest {
    @Test
    void test() {
        CBCActionDetails pAction = new CBCActionDetails();
        pAction.setAction(CBCActionType.TASK);
        ObjectNode pNode = pAction.getData();
        pNode.put("excludeConfigurationActivities", true);
        pNode.put("fillTreeStructure", false);
        pNode.put("getAll", true);
        pNode.put("projectId", "9EEC7C0447E046DA81E6F74117DAC98C");
        pAction.setOrder(0);

        List<CBCActionDetails> pActionList = new ArrayList<>();
        pActionList .add(pAction);

        CBCAction project = new CBCAction();
        project.setActions(pActionList);
        CBCPayLoadObj cbcPayLoadObj = new CBCPayLoadObj();
        cbcPayLoadObj.setProject(project);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode cbcPayloadNode = mapper.valueToTree(cbcPayLoadObj);
        System.out.println(cbcPayloadNode);
    }
}
