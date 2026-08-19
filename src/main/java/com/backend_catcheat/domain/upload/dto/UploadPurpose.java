package com.backend_catcheat.domain.upload.dto;

import java.util.Set;

// 장수 상한이 도메인마다 다르다. 기본 도감 5장은 AI 비용, 로그잇 8장은 화면·용량 기준이다
public enum UploadPurpose {

    DEFAULT(5, ContentTypes.PHOTO),
    LOGIT_RECORD(8, ContentTypes.PHOTO),
    // ImageIO가 heic/heif를 못 읽어 변환 단계에서 터진다. 업로드 시점에 막아야
    // 20초를 기다린 뒤 실패하고 일일 상한까지 깎이는 일이 없다
    ILLUSTRATION_SOURCE(1, ContentTypes.DECODABLE);

    private final int maxFileCount;
    private final Set<String> allowedContentTypes;

    UploadPurpose(int maxFileCount, Set<String> allowedContentTypes) {
        this.maxFileCount = maxFileCount;
        this.allowedContentTypes = allowedContentTypes;
    }

    public int maxFileCount() {
        return maxFileCount;
    }

    public boolean allows(String contentType) {
        return contentType != null && allowedContentTypes.contains(contentType);
    }

    // enum 상수 생성자에서는 같은 클래스의 static 필드를 참조할 수 없다
    private static final class ContentTypes {
        private static final Set<String> PHOTO =
                Set.of("image/jpeg", "image/png", "image/heic", "image/heif");
        private static final Set<String> DECODABLE =
                Set.of("image/jpeg", "image/png");
    }
}
