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
public class PCTransport extends PCArtifactDetails {
    private boolean onAssignToMeAc;
    private boolean onChangeCategoryAc;
    private boolean onCopyTransportAc;
    private boolean onMergeRequestAc;
    private boolean onRequestCheckAc;
    private boolean onRequestReleaseAc;
    private boolean onStatusRefreshAc;

    private boolean deleteMc;
    private boolean updateMc;

    private String transportRequestID;
    private String transportRequestDesc;
    private String transportRequestType;
    private String transportRequestOwner;
    private String transportRequestStatusText;

    private Date transportRequestChangedOn;  // stored as String due to /Date(...) format
    private String transportRequestChangedAt;

    private String projectId;
    private String projectDescription;
    private String transportRequestCategoryText;
    private String transportRequestTarget;
    private String logHandle;

    private boolean enableLogs;
    private int criticality;
    private boolean isFieldHidden;
    private boolean isRepositoryIdHidden;

    private String atoTransportType;
    private String transportRequestTypeText;
    private String userDescription;
    private String transportRequestStatusOld;
    private boolean enableCBCNavigation;
    private String cbcStagingID;
    private String repositoryId;
    private String transportRequestNotes;

    private String type = "pcTransport";
}
