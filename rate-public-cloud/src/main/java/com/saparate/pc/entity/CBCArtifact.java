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
@Table(name = "cbc_artifact")
public class CBCArtifact extends Auditable<String> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long roId;
    private long projectId;
    private long envId;
    private String name;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @NotFound(action = NotFoundAction.IGNORE)
    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "roLatestVersionId", referencedColumnName = "roVersionId", columnDefinition = "bigint")
    private CBCArtifactVersion cbcArtifactVersion;
    private boolean isDeleted = false;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CBCArtifact cbcArtifact = (CBCArtifact) o;
        return (projectId == cbcArtifact.projectId) && (envId == cbcArtifact.envId) && Objects.equals(name, cbcArtifact.name) && Objects.equals(cbcArtifactVersion.getVersion(), cbcArtifact.cbcArtifactVersion.getVersion())
                                                            && Objects.equals(cbcArtifactVersion.getStatus(), cbcArtifact.getCbcArtifactVersion().getStatus());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getEnvId(), getName());
    }
}
