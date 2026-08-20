package com.backend_catcheat.domain.illustration.dto;

import com.backend_catcheat.domain.illustration.entity.IllustrationMode;
import com.backend_catcheat.domain.illustration.entity.IllustrationPurpose;

// 필수 여부가 mode에 따라 갈려 검증은 IllustrationService가 한다
public record IllustrationCreateRequestDTO(
        IllustrationPurpose purpose,
        IllustrationMode mode,
        String sourceImageKey,
        String description
) {
}
