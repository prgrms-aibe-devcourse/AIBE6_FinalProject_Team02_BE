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
    // 상한이 용도마다 달라(기본 도감 5장, 로그잇 8장) 문구에 숫자를 박지 않는다
    UPLOAD_FILE_COUNT_EXCEEDED(HttpStatus.BAD_REQUEST, "한 번에 올릴 수 있는 장수를 넘었어요. 몇 장 덜어 주세요"),
    UPLOAD_FILE_INFO_REQUIRED(HttpStatus.BAD_REQUEST, "업로드할 파일 정보가 필요해요"),
    UPLOAD_FILE_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "파일 이름이 필요해요"),
    INVALID_UPLOAD_FILE(HttpStatus.BAD_REQUEST, "지원하지 않는 이미지 형식이에요"),
    // key를 알아내 남의 사진을 자기 기록에 붙이는 것을 막는다
    UPLOAD_OBJECT_NOT_OWNED(HttpStatus.FORBIDDEN, "내가 올린 사진만 쓸 수 있어요"),
    UPLOAD_OBJECT_PURPOSE_MISMATCH(HttpStatus.BAD_REQUEST, "다른 곳에 올린 사진이에요. 다시 올려 주세요"),

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
    PLACE_ADDRESS_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 주소를 찾을 수 없어요"),
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
    CHALLENGE_SLOT_MIN_REQUIRED(HttpStatus.BAD_REQUEST, "목표 음식을 최소 2개 등록해 주세요"),
    CHALLENGE_PERIOD_INVALID(HttpStatus.BAD_REQUEST, "기간 한정은 종료 시각이 시작 이후여야 해요"),
    //참여 관련
    CHALLENGE_NOT_FOUND(HttpStatus.NOT_FOUND, "챌린지를 찾을 수 없어요"),
    CHALLENGE_NOT_OWNER(HttpStatus.FORBIDDEN, "개설자만 수정하거나 삭제할 수 있어요"),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "다른 사용자가 방금 변경했어요. 다시 시도해 주세요"),
    CHALLENGE_ENDED(HttpStatus.CONFLICT, "이미 종료된 챌린지예요"),
    CHALLENGE_ALREADY_JOINED(HttpStatus.CONFLICT, "이미 참여한 챌린지예요"),
    //해금 관련
    CHALLENGE_NOT_JOINED(HttpStatus.CONFLICT, "먼저 챌린지에 참여해 주세요"),
    CHALLENGE_SLOT_NOT_FOUND(HttpStatus.NOT_FOUND, "챌린지 목표를 찾을 수 없어요"),
    CHALLENGE_SLOT_ALREADY_UNLOCKED(HttpStatus.CONFLICT, "이미 인증한 목표예요"),
    CHALLENGE_UNLOCK_IMAGE_REQUIRED(HttpStatus.BAD_REQUEST, "인증 사진을 등록해 주세요"),
    CHALLENGE_SLOT_LOCATION_REQUIRED(HttpStatus.BAD_REQUEST, "위치 인증 챌린지는 목표마다 위치가 필요해요"),
    CHALLENGE_LOCATION_REQUIRED(HttpStatus.BAD_REQUEST, "현재 위치 정보가 필요해요"),
    CHALLENGE_LOCATION_TOO_FAR(HttpStatus.BAD_REQUEST, "목표 위치에서 너무 멀어요"),

    //리뷰
    //리뷰 (음식 리뷰=해금 후 / 챌린지 리뷰=완료 후)
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "리뷰를 찾을 수 없어요"),
    REVIEW_FORBIDDEN(HttpStatus.FORBIDDEN, "내가 쓴 리뷰만 고치거나 지울 수 있어요"),
    REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 리뷰를 남겼어요"),
    REVIEW_REQUIRES_UNLOCK(HttpStatus.CONFLICT, "이 음식을 인증한 뒤에 리뷰를 쓸 수 있어요"),
    REVIEW_REQUIRES_COMPLETION(HttpStatus.CONFLICT, "챌린지를 완료한 뒤에 리뷰를 쓸 수 있어요"),
    REVIEW_CONTENT_TOO_LONG(HttpStatus.BAD_REQUEST, "리뷰는 500자까지 쓸 수 있어요"),
    REVIEW_RATING_INVALID(HttpStatus.BAD_REQUEST, "별점은 1~5 사이로 골라 주세요"),

    // 제작 도감
    MADE_DEX_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "도감 이름을 입력해 주세요"),
    MADE_DEX_NAME_TOO_LONG(HttpStatus.BAD_REQUEST, "도감 이름은 100자까지 쓸 수 있어요"),
    MADE_DEX_DESCRIPTION_TOO_LONG(HttpStatus.BAD_REQUEST, "도감 소개는 500자까지 쓸 수 있어요"),
    MADE_DEX_IMAGE_KEY_TOO_LONG(HttpStatus.BAD_REQUEST, "이미지를 등록하지 못했어요. 다시 시도해 주세요"),
    MADE_DEX_NOT_FOUND(HttpStatus.NOT_FOUND, "제작 도감을 찾을 수 없어요"),
    MADE_DEX_NOT_OWNER(HttpStatus.FORBIDDEN, "그룹장만 할 수 있어요"),
    MADE_DEX_FULL(HttpStatus.CONFLICT, "인원이 가득 찬 도감이에요"),
    // 초대 코드 / 참여
    MADE_DEX_INVITE_CODE_REQUIRED(HttpStatus.BAD_REQUEST, "초대 코드를 입력해 주세요"),
    MADE_DEX_INVITE_CODE_INVALID(HttpStatus.NOT_FOUND, "존재하지 않는 초대 코드예요"),
    MADE_DEX_INVITE_CODE_EXPIRED(HttpStatus.GONE, "만료된 초대 코드예요. 새 코드를 요청해 주세요"),
    MADE_DEX_ALREADY_JOINED(HttpStatus.CONFLICT, "이미 이 제작 도감에 참여 중이에요"),
    // 멤버 관리(추방 / 탈퇴 / 위임)
    MADE_DEX_NOT_MEMBER(HttpStatus.FORBIDDEN, "참여 중인 제작 도감이 아니에요"),
    MADE_DEX_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "이미 나간 참여자예요"),
    MADE_DEX_CANNOT_KICK_SELF(HttpStatus.BAD_REQUEST, "스스로를 내보낼 수는 없어요. 나가기를 눌러 주세요"),
    MADE_DEX_ALREADY_OWNER(HttpStatus.CONFLICT, "이미 그룹장이에요"),
    // 끼니 슬롯 (로그잇)
    MADE_DEX_SLOT_NOT_FOUND(HttpStatus.NOT_FOUND, "끼니 슬롯을 찾을 수 없어요"),
    MADE_DEX_SLOT_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "슬롯 이름을 입력해 주세요"),
    MADE_DEX_SLOT_NAME_TOO_LONG(HttpStatus.BAD_REQUEST, "슬롯 이름은 20자까지 쓸 수 있어요"),
    MADE_DEX_SLOT_NAME_DUPLICATED(HttpStatus.CONFLICT, "같은 이름의 슬롯이 이미 있어요"),
    MADE_DEX_SLOT_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "슬롯은 6개까지 만들 수 있어요"),
    MADE_DEX_SLOT_LAST_ONE(HttpStatus.BAD_REQUEST, "슬롯을 모두 없앨 수는 없어요. 최소 한 개는 남겨 주세요"),
    MADE_DEX_SLOT_ORDER_MISMATCH(HttpStatus.BAD_REQUEST, "순서가 바뀌는 중이에요. 새로고침 후 다시 시도해 주세요"),
    MADE_DEX_SLOT_HIDDEN(HttpStatus.BAD_REQUEST, "지금은 쓰지 않는 슬롯이에요"),
    // 식사 기록 (로그잇)
    MADE_DEX_RECORD_NOT_FOUND(HttpStatus.NOT_FOUND, "기록을 찾을 수 없어요"),
    MADE_DEX_RECORD_NOT_AUTHOR(HttpStatus.FORBIDDEN, "내가 남긴 기록만 고칠 수 있어요"),
    MADE_DEX_RECORD_PHOTO_REQUIRED(HttpStatus.BAD_REQUEST, "사진을 한 장 이상 올려 주세요"),
    MADE_DEX_RECORD_PHOTO_TOO_MANY(HttpStatus.BAD_REQUEST, "사진은 8장까지 올릴 수 있어요"),
    MADE_DEX_RECORD_PHOTO_NOT_FOUND(HttpStatus.BAD_REQUEST, "이 기록의 사진이 아니에요"),
    MADE_DEX_RECORD_CAPTION_TOO_LONG(HttpStatus.BAD_REQUEST, "사진에 붙이는 글은 100자까지 쓸 수 있어요"),
    MADE_DEX_RECORD_DATE_REQUIRED(HttpStatus.BAD_REQUEST, "날짜를 골라 주세요"),
    MADE_DEX_RECORD_FUTURE_DATE(HttpStatus.BAD_REQUEST, "아직 오지 않은 날은 기록할 수 없어요"),
    // 로그잇은 "오늘"을 나누는 앱이다. 지난 날 등록은 막고 열람만 남긴다
    MADE_DEX_RECORD_PAST_DATE(HttpStatus.BAD_REQUEST, "오늘 먹은 것만 기록할 수 있어요. 날짜가 바뀌었는지 확인해 주세요"),
    MADE_DEX_RECORD_SLOT_TAKEN(HttpStatus.CONFLICT, "이미 기록한 끼니예요. 고치거나 지운 뒤에 다시 올려 주세요"),
    MADE_DEX_RECORD_PAST_LOCKED(HttpStatus.BAD_REQUEST, "지난 기록은 사진에 붙인 글만 고칠 수 있어요"),

    // 뱃지
    BADGE_NOT_OWNED(HttpStatus.BAD_REQUEST, "보유하지 않은 뱃지예요"),

    // 챌린지 보상 뱃지(커스텀/프리셋)
    REWARD_BADGE_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "뱃지 이름을 입력해 주세요"),
    REWARD_BADGE_SOURCE_REQUIRED(HttpStatus.BAD_REQUEST, "프리셋을 고르거나 이미지를 만들어 주세요"),
    INVALID_PRESET_CODE(HttpStatus.BAD_REQUEST, "존재하지 않는 프리셋이에요"),
    REWARD_BADGE_NOT_FOUND(HttpStatus.NOT_FOUND, "보상 뱃지를 찾을 수 없어요"),
    REWARD_BADGE_NAME_TOO_LONG(HttpStatus.BAD_REQUEST, "뱃지 이름은 100자까지 쓸 수 있어요"),

    // 친구
    FRIEND_SELF_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "자기 자신에게는 친구 요청을 보낼 수 없어요"),
    FRIEND_ALREADY(HttpStatus.CONFLICT, "이미 친구예요"),
    FRIEND_REQUEST_ALREADY_SENT(HttpStatus.CONFLICT, "이미 친구 요청을 보냈어요"),
    FRIEND_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "친구 요청을 찾을 수 없어요"),
    FRIEND_NOT_FOUND(HttpStatus.NOT_FOUND, "친구 관계가 아니에요"),
    FRIEND_FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없어요");





    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
