package com.saparate.pc.exception;

public class ROPublicCloudException extends Exception {

    private static final long serialVersionUID = 1L;

    public ROPublicCloudException(String message, Throwable cause) {
        super(message, cause);
    }

    public ROPublicCloudException(String message) {
        super(message);
    }

    public ROPublicCloudException(Throwable cause) {
        super(cause);
    }
}
