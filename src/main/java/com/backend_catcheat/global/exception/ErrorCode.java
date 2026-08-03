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

    // 닉네임
    NICKNAME_INVALID(HttpStatus.BAD_REQUEST, "닉네임 형식을 확인해 주세요"),
    NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 닉네임이에요"),
    NICKNAME_ALREADY_SET(HttpStatus.CONFLICT, "이미 닉네임이 설정되어 있어요"),
    NICKNAME_CHANGE_TOO_SOON(HttpStatus.BAD_REQUEST, "닉네임은 한 달에 한 번만 바꿀 수 있어요"),

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

    // 메모 템플릿
    MEMO_TEMPLATE_NOT_FOUND(HttpStatus.NOT_FOUND, "메모 템플릿을 찾을 수 없어요"),
    MEMO_TEMPLATE_CONTENT_REQUIRED(HttpStatus.BAD_REQUEST, "저장할 메모 내용을 적어 주세요"),
    MEMO_TEMPLATE_TOO_LONG(HttpStatus.BAD_REQUEST, "메모 템플릿은 100자까지 저장할 수 있어요"),
    MEMO_TEMPLATE_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "메모 템플릿은 3개까지 저장할 수 있어요. 하나를 지우고 다시 저장해 주세요"),

    // 장소 검색 (카카오 로컬)
    // 카카오 장애는 우리 서버 버그가 아니다. 500으로 두면 모니터링에서 진짜 장애와 섞인다
    PLACE_SEARCH_FAILED(HttpStatus.BAD_GATEWAY, "장소를 찾지 못했어요. 직접 입력해 주세요"),

    // 관리자 콘솔 (제보 큐 / 음식 등록 요청 큐)
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "제보를 찾을 수 없어요"),
    REGISTRATION_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "등록 요청을 찾을 수 없어요"),
    ADMIN_ITEM_ALREADY_HANDLED(HttpStatus.CONFLICT, "이미 처리한 항목이에요"),



    //제보
    REPORT_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "제보할 음식 이름을 입력해주세요"),


    //챌린지
    //티켓
    CHALLENGE_TICKET_EXHAUSTED(HttpStatus.CONFLICT, "이번 달 챌린지 개설권을 모두 사용했어요"),
    //개설 검증 코드
    CHALLENGE_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "챌린지 이름을 입력해 주세요"),
    CHALLENGE_TYPE_REQUIRED(HttpStatus.BAD_REQUEST, "챌린지 유형과 기한 방식을 선택해 주세요"),
    CHALLENGE_SLOT_MIN_REQUIRED(HttpStatus.BAD_REQUEST, "목표 음식을 최소 5개 등록해 주세요"),
    CHALLENGE_PERIOD_INVALID(HttpStatus.BAD_REQUEST, "기간 한정은 종료 시각이 시작 이후여야 해요"),
    //참여 관련
    CHALLENGE_NOT_FOUND(HttpStatus.NOT_FOUND, "챌린지를 찾을 수 없어요"),
    CHALLENGE_ENDED(HttpStatus.CONFLICT, "이미 종료된 챌린지예요"),
    CHALLENGE_ALREADY_JOINED(HttpStatus.CONFLICT, "이미 참여한 챌린지예요"),

    // 제작 도감
    MADE_DEX_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "도감 이름을 입력해 주세요"),
    MADE_DEX_NAME_TOO_LONG(HttpStatus.BAD_REQUEST, "도감 이름은 100자까지 쓸 수 있어요"),
    MADE_DEX_DESCRIPTION_TOO_LONG(HttpStatus.BAD_REQUEST, "도감 소개는 500자까지 쓸 수 있어요"),

    // 뱃지
    BADGE_NOT_OWNED(HttpStatus.BAD_REQUEST, "보유하지 않은 뱃지예요");




    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
