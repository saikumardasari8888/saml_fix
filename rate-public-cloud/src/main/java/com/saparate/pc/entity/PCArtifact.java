package com.saparate.pc.entity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rate.commons.entity.Auditable;
import com.saparate.pc.model.PCArtifactType;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Objects;

@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "pc_artifact")
public class PCArtifact extends Auditable<String> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long roId;
    private long projectId;
    private long envId;
    private String artifactId;
    private String name; //collection -name , Tr- Desc
    private PCArtifactType type;

    private boolean isDeleted = false;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @NotFound(action = NotFoundAction.IGNORE)
    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "roLatestVersionId", referencedColumnName = "roVersionId", columnDefinition = "bigint")
    private PCArtifactVersion pcArtifactVersion;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PCArtifact that = (PCArtifact) o;
        return projectId == that.projectId && envId == that.envId && Objects.equals(artifactId, that.artifactId) && type == that.type && Objects.equals(pcArtifactVersion, that.pcArtifactVersion);
    }
//    @Override
//    public boolean equals(Object o) {
//        if (this == o) return true;
//        if (o == null || getClass() != o.getClass()) return false;
//        PCArtifact pcArtifact = (PCArtifact) o;
//        return Objects.equals(artifactId, pcArtifact.artifactId) && type == pcArtifact.type
//                && (Objects.equals(pcArtifactVersion.getSapChangedOn(), pcArtifact.pcArtifactVersion.getSapChangedOn()) || Math.abs(pcArtifactVersion.getSapChangedOn().getTime() - pcArtifact.pcArtifactVersion.getSapChangedOn().getTime()) < 5000);
//    }

    @Override
    public int hashCode() {
        return Objects.hash(artifactId, envId, type);
    }
}
