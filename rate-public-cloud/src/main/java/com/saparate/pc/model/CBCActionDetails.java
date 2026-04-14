package com.saparate.pc.model;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class CBCActionDetails {
    private String action;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private ObjectNode data = createObjectNode();
    private int order = 0;

    private ObjectNode createObjectNode() {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.createObjectNode();
    }
}
