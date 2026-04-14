package com.saparate.pc.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PCTransport.class, name = "pcTransport"),
        @JsonSubTypes.Type(value = PCSoftware.class, name = "pcSoftware"),
})
public class PCArtifactDetails {
    public String getType() {
        return "PcArtifactDetails";
    }
}
