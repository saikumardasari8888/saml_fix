package com.saparate.pc.model;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class PCDependencyValidationReport {
    private String artifactId;
    private List<PCArtifactValidationResult> dependencies;
}
