package com.backend_catcheat.domain.registration.service;

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
