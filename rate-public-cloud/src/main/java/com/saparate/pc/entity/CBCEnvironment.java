package com.saparate.pc.entity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import com.rate.commons.entity.Auditable;
import com.rate.resources.ResourceType;
import com.rate.user.entity.IEnvironment;
import com.saparate.pc.model.CBCTargetSystemDetails;
import io.hypersistence.utils.hibernate.type.json.JsonStringType;
import org.hibernate.annotations.Type;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "cbc_environment")
public class CBCEnvironment extends Auditable<String> implements IEnvironment {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;
    private String name;
    @Column(columnDefinition = "varchar(20) NOT NULL default 'sapcbc'")
    protected String envType = ResourceType.SAP_CBC_ENV.getResourceType();
    @Column(columnDefinition = "varchar(20) NOT NULL default 'sapcbc'")
    protected String resourceType = ResourceType.SAP_CBC_ENV.getResourceType();
    @Column(length = 100)
    private Long credentialId;
    @Column(length = 500)
    private String hostUrl;
    private String workSpaceType;
    private String workSpaceId;
    private String cbcProjectId;
    private String deployTargetId;
    @Type(JsonStringType.class)
    @Column(columnDefinition = "json")
    private CBCTargetSystemDetails cbcTargetSystemDetails;
    @Column(columnDefinition = "varchar(25) NULL default NULL")
    private String systemType;
}
