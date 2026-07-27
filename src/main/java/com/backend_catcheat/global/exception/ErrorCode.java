package com.backend_catcheat.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 전역 에러 코드
 */
@Getter
public enum ErrorCode {

    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "잠시 후 다시 시도해 주세요"),

    // 회원 / 온보딩
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없어요"),

    // 업로드시 올바르지 않은 요청
    UPLOAD_FILE_REQUIRED(HttpStatus.BAD_REQUEST, "업로드할 파일이 필요해요"),
    UPLOAD_FILE_COUNT_EXCEEDED(HttpStatus.BAD_REQUEST, "사진은 한 번에 최대 5장까지 등록할 수 있어요"),
    UPLOAD_FILE_INFO_REQUIRED(HttpStatus.BAD_REQUEST, "업로드할 파일 정보가 필요해요"),
    UPLOAD_FILE_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "파일 이름이 필요해요"),
    INVALID_UPLOAD_FILE(HttpStatus.BAD_REQUEST, "지원하지 않는 이미지 형식이에요");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
