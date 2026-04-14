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

import com.rate.commons.entity.Auditable;
import com.rate.commons.enums.ReleaseForType;
import com.saparate.pc.enums.PCArtifactDeployLogStatus;
import com.saparate.pc.model.PCArtifactType;
import io.hypersistence.utils.hibernate.type.json.JsonStringType;
import org.hibernate.annotations.Type;

import java.util.Date;
import java.util.Map;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Entity
@Table(name = "pc_artifact_deploy_log")
public class PCArtifactDeployLog extends Auditable<String> implements Cloneable{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    private long roPcArtifactId;
    private String pcArtifactName;
    private String sapPcArtifactId;
    private long roPcArtifactVersionId;
    private String version;
    private long userStoryId;
    @Column(columnDefinition = "varchar(20)")
    private String projUsId;
    @Column(columnDefinition = "varchar(20)")
    private String extUsId;

    private PCArtifactType artifactType;

    private long releaseForId;
    @Column(columnDefinition = "varchar(20)")
    private ReleaseForType releaseForType;
    private long pcDeployEnvId;
    private long projectId;

    @Column(columnDefinition = "varchar(50)")
    private String wfTaskId;

    @Column(columnDefinition = "varchar(10)")
    private PCArtifactDeployLogStatus deployStatus = PCArtifactDeployLogStatus.EMPTY;

    @Column(columnDefinition = "text")
    private String deployErrorMsg;
    @Type(JsonStringType.class)
    @Column(columnDefinition = "json")
    private Map<String, String> pcArtifactContextMap;

    private boolean isDeployed = false;

    private String deployedType = "Auto"; // Manual or Auto
    private String manualDeployedBy;
    @Column(columnDefinition = "text")
    private String manualDeployedReason;
    private Date manualDeployDate;

    private boolean isAlreadyDeployedManualInSap = false; // artifact is already deployed manual in public cloud system.

    @Transient
    private PCEnvironment pcEnvironment;

    @Override
    public PCArtifactDeployLog clone() throws CloneNotSupportedException {
        return (PCArtifactDeployLog) super.clone();
    }
}
