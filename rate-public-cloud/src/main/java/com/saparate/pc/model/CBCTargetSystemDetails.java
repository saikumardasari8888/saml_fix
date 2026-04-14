package com.saparate.pc.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class CBCTargetSystemDetails implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;
    private long cbcEnvId;
    private String uuId;
    private String name;
    private String cbcTenantUrl;
    private String cbcTenantId;
    private String type;
    private String operationStatus;
    @JsonProperty("demoDataIncluded")
    private boolean isDemoDataIncluded;

}
