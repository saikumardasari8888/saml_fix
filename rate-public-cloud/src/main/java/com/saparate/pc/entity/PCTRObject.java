package com.saparate.pc.entity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import tools.jackson.databind.JsonNode;
import com.rate.commons.entity.Auditable;
import io.hypersistence.utils.hibernate.type.json.JsonStringType;
import org.hibernate.annotations.Type;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Entity
@Table(name = "pc_tr_object")
public class PCTRObject extends Auditable<String> {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;
    private long projectId;
    private long envId;
    private long roArtifactId;
    private String sapTRId;
    private String trName; // Transport Description
    @Type(JsonStringType.class)
    @Column(columnDefinition = "json")
    private JsonNode pcTrObjects;
}
