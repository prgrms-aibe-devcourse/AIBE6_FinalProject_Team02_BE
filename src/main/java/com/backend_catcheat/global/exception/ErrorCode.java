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
    INVALID_UPLOAD_FILE(HttpStatus.BAD_REQUEST, "지원하지 않는 이미지 형식이에요"),

    // 등록 — 입력 검증
    PHOTO_REQUIRED(HttpStatus.BAD_REQUEST, "사진을 최소 1장 올려 주세요"),
    PHOTO_COUNT_EXCEEDED(HttpStatus.BAD_REQUEST, "사진은 최대 5장까지 올릴 수 있어요"),
    ANALYSIS_PHOTO_INVALID(HttpStatus.BAD_REQUEST, "분석할 사진을 다시 선택해 주세요"),
    FOOD_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "음식을 최소 하나 골라 주세요"),
    FOOD_NAME_COUNT_EXCEEDED(HttpStatus.BAD_REQUEST, "음식은 한 번에 최대 5개까지 등록할 수 있어요"),
    DEX_SLOT_NOT_FOUND(HttpStatus.BAD_REQUEST, "도감에 없는 음식이에요"),

    // 등록 — 진행 상태
    REGISTRATION_NOT_FOUND(HttpStatus.NOT_FOUND, "등록 정보를 찾을 수 없어요"),
    REGISTRATION_FORBIDDEN(HttpStatus.FORBIDDEN, "본인의 등록만 이어서 진행할 수 있어요"),
    // 재분석 상한 2회 초과 — 수동 폴백으로 안내한다 (§5.2)
    RETRY_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "다시 확인할 수 있는 횟수를 다 썼어요"),

    // 등록 — 사진 처리
    PHOTO_NOT_UPLOADED(HttpStatus.BAD_REQUEST, "사진 업로드가 끝나지 않았어요"),
    PHOTO_TOO_LARGE(HttpStatus.BAD_REQUEST, "사진 한 장은 10MB까지예요"),
    IMAGE_DECODE_FAILED(HttpStatus.BAD_REQUEST, "사진을 읽을 수 없어요"),
    IMAGE_ENCODE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "사진을 변환할 수 없어요"),

    // 등록 — AI
    AI_KEY_MISSING(HttpStatus.INTERNAL_SERVER_ERROR, "AI 설정이 준비되지 않았어요"),
    AI_CALL_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "음식 확인에 실패했어요. 잠시 후 다시 시도해 주세요"),
    AI_RESPONSE_PARSE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "음식 확인 결과를 읽지 못했어요"),

    // 등록 — 확정(해금)
    CARD_REQUIRED(HttpStatus.BAD_REQUEST, "등록할 음식이 없어요"),
    REGISTRATION_ALREADY_COMPLETED(HttpStatus.CONFLICT, "이미 등록이 끝난 건이에요"),
    // 검증을 통과하지 않은 칸으로는 해금할 수 없다 (§5.2)
    SLOT_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "확인되지 않은 음식은 등록할 수 없어요"),
    PHOTO_NOT_IN_REGISTRATION(HttpStatus.BAD_REQUEST, "이 등록 건의 사진이 아니에요"),
    DUPLICATE_PHOTO(HttpStatus.CONFLICT, "이미 등록된 사진이에요"),
    MEMO_TOO_LONG(HttpStatus.BAD_REQUEST, "메모는 100자까지 쓸 수 있어요"),

    // 관리자 검토
    REVIEW_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "검토 항목을 찾을 수 없어요"),
    REVIEW_ALREADY_HANDLED(HttpStatus.CONFLICT, "이미 처리한 검토 항목이에요");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
