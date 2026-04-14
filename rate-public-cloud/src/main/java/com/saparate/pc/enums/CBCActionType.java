package com.saparate.pc.enums;

public interface CBCActionType {

    String USER_PROJECTS = "getUserProjects";
    String USER_WORKSPACE = "getUserWorkspaces";
    String TARGET_DEPLOYMENT_DETAILS = "getAssignedDeploymentTarget";
    String TASK = "getTasks";
    String CHECK_ALLOW_DEPLOY = "isDeployAllowed";
    String CBC_DEPLOY = "updateMilestoneStatus";
    String CBC_DEPLOYMENT_PROGRESS = "getMilestoneProgress";
    String CBC_AUDIT_LOG = "getAuditLog";
    String CBC_DEPLOYMENT_ACTION = "getDeploymentTargetHistory";
}
