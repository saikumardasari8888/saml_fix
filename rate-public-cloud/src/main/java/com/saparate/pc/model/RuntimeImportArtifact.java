package com.saparate.pc.model;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class RuntimeImportArtifact {
    private String artifactId;
//    private String name;
    private String version;
    private String importedBy;
    private Date importedOn;
    private String importStatus;
    private String importStatusDesc;
    private int cbcDeploymentPercentage;
    private String cbcDeployProcessTime;
}
