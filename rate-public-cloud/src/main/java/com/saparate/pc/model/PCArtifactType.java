package com.saparate.pc.model;

import java.util.stream.Stream;

public enum PCArtifactType {
    SOFTWARE_COLLECTION(0, "Software Collection"), TRANSPORT (1, "Transport");

    private String name;
    private int code;


    private PCArtifactType( int code, String name) {
        this.name = name;
        this.code=code;
    }
    public String getName() {
        return this.name;
    }
    public int getCode() {
        return this.code;
    }
    public static PCArtifactType of(int code) {
        return Stream.of(PCArtifactType.values())
                .filter(artifactType -> (code == artifactType.getCode()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(String.format("Unable to find CpiArtifactType for given code %s", code)));
    }

    public static PCArtifactType of(String name) {
        return Stream.of(PCArtifactType.values())
                .filter(artifactMode -> (name.equalsIgnoreCase(artifactMode.getName())))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(String.format("Unable to find PCArtifactType for given name %s", name)));
    }
}
