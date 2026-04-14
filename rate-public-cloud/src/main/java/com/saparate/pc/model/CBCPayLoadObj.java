package com.saparate.pc.model;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import com.fasterxml.jackson.annotation.JsonInclude;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class CBCPayLoadObj {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private CBCAction project;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private CBCAction workspace;
}
