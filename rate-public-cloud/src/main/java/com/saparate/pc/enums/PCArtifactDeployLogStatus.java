package com.saparate.pc.enums;

import lombok.Getter;

import java.util.stream.Stream;

@Getter
public enum PCArtifactDeployLogStatus {
    NOT_STARTED("Not Started"), IN_PROGRESS("In Progress"),
    FAILED("Failed"), SUCCESS("Success"), EMPTY("-"),
    NOT_PRESENT("Not Present"), TIMEOUT("Timeout"), OVERWRITE("OverWrite");

    private final String statusMsg;

    PCArtifactDeployLogStatus(String msg) {
        this.statusMsg = msg;
    }

    public static PCArtifactDeployLogStatus of(int code) {
        return Stream.of(PCArtifactDeployLogStatus.values())
                .filter(artifactType -> (code == artifactType.ordinal()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(String.format("Unable to find Public Cloud ArtifactDeployLogStatus for given code %s", code)));
    }
}
