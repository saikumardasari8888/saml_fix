package com.saparate.pc.entity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.rate.commons.entity.Auditable;
import com.rate.commons.util.sync.SyncStatus;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Setter
@ToString
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "pc_artifact_sync_log")
public class PCArtifactSyncLog extends Auditable<String> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private long projectId;

    private long envId;

    private SyncStatus syncStatus;

    private long changeCount;

    private String type; // PcArtifact, CBCArtifact

    private String sapArtifactId;

    private String name;

    @Column(columnDefinition = "text")
    private String syncError;
}
