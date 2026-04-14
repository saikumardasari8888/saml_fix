package com.saparate.pc.model;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class CBCWorkSpace {
    private String workSpaceType;
    private String workSpaceId;
    private String cbcProjectId;
    private String deployTargetId;
}
