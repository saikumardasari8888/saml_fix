package com.saparate.pc.model;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Date;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class PCArtifactValidationResult {

    private String artifactId;
    private String version;
    private String description;
    private String statusDescription;
    private String artifactTypeDescription;
    private String downtimeDescription;
    private Date exportedDate;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String pcStatus;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String status;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String message;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String dependentArtifactId;
    private long userStoryId;
    private String projUsId;
    private String extUsId;


}
