package com.backend_catcheat.domain.illustration.service;

import com.backend_catcheat.domain.illustration.config.IllustrationProperties;
import com.backend_catcheat.global.s3.S3ObjectWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IllustrationResultStore {

    private static final String KEY_PREFIX = "illustrations";

    private final IllustrationImageCodec imageCodec;
    private final S3ObjectWriter s3ObjectWriter;
    private final IllustrationProperties properties;

    // 생성 원본 1024px는 2.4MB다. 표시 최대가 240px이라 그대로 두면 20배 큰 파일이 걸린다
    public String store(byte[] generated) {
        byte[] shrunk = imageCodec.shrinkPng(generated, properties.storedMaxEdgePx());
        return s3ObjectWriter.put(KEY_PREFIX, shrunk, MediaType.IMAGE_PNG_VALUE, ".png");
    }
}
