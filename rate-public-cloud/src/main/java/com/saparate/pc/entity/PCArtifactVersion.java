package com.saparate.pc.entity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import com.saparate.pc.model.PCArtifactDetails;
import io.hypersistence.utils.hibernate.type.json.JsonStringType;
import org.hibernate.annotations.Type;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;
import java.util.Objects;

@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "pc_artifact_version")
public class PCArtifactVersion extends Auditable<String> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long roVersionId;
    private long roArtifactId;
    private long envId;
    private long projectId;
    private String version;
    private String description;
    private String sapChangedBy;
    private Date sapChangedOn;

    @Type(JsonStringType.class)
    @Column(columnDefinition = "json")
    private PCArtifactDetails pcArtifactDetails;

    @Transient
    private long userStoryId;
    @Transient
    private String projUserStoryId;
    @Transient
    private String extUsId;
    @Transient
    private Date usAssociatedDate;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PCArtifactVersion that = (PCArtifactVersion) o;
        return roArtifactId == that.roArtifactId && envId == that.envId && projectId == that.projectId && Objects.equals(version, that.version) && Objects.equals(sapChangedOn, that.sapChangedOn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roArtifactId, envId, projectId, version);
    }
}
