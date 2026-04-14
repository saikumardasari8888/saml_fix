package com.saparate.pc.entity;
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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "pc_environment")
public class PCEnvironment extends Auditable<String> implements IEnvironment {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;
    private String name;
    @Column(columnDefinition = "varchar(20) NOT NULL default 'publiccloud'")
    protected String envType = ResourceType.PUBLIC_CLOUD_ENV.getResourceType();
    @Column(columnDefinition = "varchar(20) NOT NULL default 'publiccloud'")
    protected String resourceType = ResourceType.PUBLIC_CLOUD_ENV.getResourceType();
    @Column(length = 100)
    private Long credentialId;
    @Column(length = 500)
    private String hostUrl;
    private String pcSystemType;
    private String clientId;
    @Column(columnDefinition = "varchar(25) NULL default NULL")
    private String systemType;

}
