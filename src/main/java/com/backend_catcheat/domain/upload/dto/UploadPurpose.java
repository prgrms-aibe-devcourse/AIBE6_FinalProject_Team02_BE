package com.backend_catcheat.domain.upload.dto;

// 장수 상한이 도메인마다 다르다. 기본 도감 5장은 AI 비용, 로그잇 8장은 화면·용량 기준이다
public enum UploadPurpose {

    DEFAULT(5),
    LOGIT_RECORD(8);

    private final int maxFileCount;

    UploadPurpose(int maxFileCount) {
        this.maxFileCount = maxFileCount;
    }

    public int maxFileCount() {
        return maxFileCount;
    }

    // 용도를 보내지 않던 기존 화면은 그대로 5장이다
    public static UploadPurpose orDefault(UploadPurpose purpose) {
        return purpose == null ? DEFAULT : purpose;
    }
}
