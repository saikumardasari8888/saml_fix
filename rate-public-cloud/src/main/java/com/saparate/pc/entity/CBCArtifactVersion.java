package com.saparate.pc.entity;
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

import tools.jackson.databind.JsonNode;
import com.rate.commons.entity.Auditable;
import org.hibernate.annotations.Type;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;

@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "cbc_artifact_version")
public class CBCArtifactVersion extends Auditable<String> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long roVersionId;
    private long projectId;
    private long roArtifactId;
    private long cbcEnvId;
    private String version;
    private String uuid;
    private String elementId; // consider as type
    private String status;
    private String description;
    private String detailedDescription;
    private Date dueDate;
    private Date startDate;
    private Date timeStamp;
    private String assigneeUUID;
    private String title;
    private String parent;
    private String actualParent;
    private String activityGroupId;
    private String goLiveRelevantCode;
    private boolean milestone;
    private String phase;
    private int sequence;
    private String type;
    private String businessArea;

    @Transient
    private long userStoryId;
    @Transient
    private String projUserStoryId;
    @Transient
    private String extUsId;
    @Transient
    private Date usAssociatedDate;
    @Transient
    private String usSummary;
}
