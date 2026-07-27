package com.backend_catcheat.spike.vision.exception;

public class VisionSpikeException extends RuntimeException {

    private final String code;

    public VisionSpikeException(String code, String message) {
        super(message);
        this.code = code;
    }

    public VisionSpikeException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
