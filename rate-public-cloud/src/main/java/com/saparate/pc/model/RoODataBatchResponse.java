package com.saparate.pc.model;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import tools.jackson.databind.JsonNode;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class RoODataBatchResponse {
    private int statusCode;
    private JsonNode jsonBody;
}
