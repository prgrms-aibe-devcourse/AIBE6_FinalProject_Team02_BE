package com.backend_catcheat.spike.vision.service;

public record PreparedImage(
        byte[] data,
        long originalBytes,
        int width,
        int height,
        String originalName
) {

    public long encodedBytes() {
        return data.length;
    }
}
