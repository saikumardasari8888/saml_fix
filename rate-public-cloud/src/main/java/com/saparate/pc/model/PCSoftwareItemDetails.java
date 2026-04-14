package com.saparate.pc.model;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class PCSoftwareItemDetails {
    private String itemId;
    private String itemName;
    private String itemType;
    private String itemStatus;
    private String itemStatusDescription;
    private String itemChangedBy;
    private String itemChangedByDescription;
    private Date itemChangedOn;
}
