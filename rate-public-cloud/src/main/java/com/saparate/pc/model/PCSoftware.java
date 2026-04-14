package com.saparate.pc.model;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;
import java.util.List;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class PCSoftware extends PCArtifactDetails {
    private String collectionId;
    private String version;
    private boolean isLatestVersion;
    private Date timestamp;

    private String description;
    private String category;
    private String status;
    private String statusDescription;

    private String action;
    private String actionDescription;
    private String actionVariant;
    private String actionStatus;
    private String actionStatusDescription;
    private String actionText;

    private String changedBy;
    private String changedByDescription;
    private Date changedAt;

    private int noOfVlogs;
    private String currentBallogHandle;
    private int noOfCollectionItems;
    private int noOfVnotes;
    private String developmentNamespace;
    private List<PCSoftwareItemDetails> itemDetails;
    private String type = "pcSoftware";
}
