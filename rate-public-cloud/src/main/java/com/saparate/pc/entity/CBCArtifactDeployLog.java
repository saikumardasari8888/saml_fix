package com.saparate.pc.entity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.JsonNode;
import com.rate.commons.entity.Auditable;
import com.rate.commons.enums.ReleaseForType;
import com.saparate.pc.enums.PCArtifactDeployLogStatus;
import io.hypersistence.utils.hibernate.type.json.JsonStringType;
import org.hibernate.annotations.Type;

import java.util.Date;
import java.util.Map;
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Entity
@Table(name ="cbc_artifact_deploy_log")
public class CBCArtifactDeployLog extends Auditable<String> implements Cloneable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    private long roCBCArtifactId;
    private String cbcArtifactName;
    private long roCBCArtifactVersion;
    private long roCbcArtifactVersionId;
    private String version;
    private long userStoryId;
    @Column(columnDefinition = "varchar(20)")
    private String projUsId;
    @Column(columnDefinition = "varchar(20)")
    private String extUsId;
    private long releaseForId;
    @Column(columnDefinition = "varchar(20)")
    private ReleaseForType releaseForType;
    private long cbcDeployEnvId;
    private long projectId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Type(JsonStringType.class)
    @Column(columnDefinition = "json")
    private JsonNode deploymentTargetDetails;
    private String externalStagingId;
    private Date sapCbcDeploymentDate;
    private String cbcTenantUrl;

    @Column(columnDefinition = "varchar(50)")
    private String wfTaskId;

    @Column(columnDefinition = "varchar(10)")
    private PCArtifactDeployLogStatus deployStatus = PCArtifactDeployLogStatus.EMPTY;

    @Column(columnDefinition = "text")
    private String deployErrorMsg;
    @Type(JsonStringType.class)
    @Column(columnDefinition = "json")
    private Map<String, String> cbcContextMap;

    private int cbcDeployPercentage;
    private String cbcDeployProcessTime;

    private boolean isDeployed;

    private String deployedType = "Auto"; // Manual or Auto
    private String manualDeployedBy;
    @Column(columnDefinition = "text")
    private String manualDeployedReason;
    private Date manualDeployDate;

    @Transient
    private CBCEnvironment cbcEnvironment;

    @Override
    public CBCArtifactDeployLog clone() throws CloneNotSupportedException {
        return (CBCArtifactDeployLog) super.clone();
    }
}
