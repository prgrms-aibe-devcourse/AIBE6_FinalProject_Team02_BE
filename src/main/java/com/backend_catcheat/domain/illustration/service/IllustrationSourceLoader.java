package com.backend_catcheat.domain.illustration.service;

import com.backend_catcheat.domain.illustration.config.IllustrationProperties;
import com.backend_catcheat.domain.registration.service.ImagePreprocessor;
import com.backend_catcheat.domain.registration.service.PreparedImage;
import com.backend_catcheat.domain.registration.service.RegistrationPhotoLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IllustrationSourceLoader {

    private final RegistrationPhotoLoader photoLoader;
    private final ImagePreprocessor imagePreprocessor;
    private final IllustrationProperties properties;

    // 축소하지 않고 보내면 12MP 스마트폰 원본이 크기 때문에 거절된다
    public PreparedImage load(String imageKey) {
        byte[] original = photoLoader.loadForAnalysis(imageKey);
        return imagePreprocessor.prepare(original, imageKey, properties.sourceMaxEdgePx());
    }
}
