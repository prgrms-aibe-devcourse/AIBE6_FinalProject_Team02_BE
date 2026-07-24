package com.backend_catcheat.global.common;

/**
 * 모든 API의 공통 응답 래퍼. (AGENTS.md §6 규약)
 * 성공: { "success": true,  "data": {...} }
 * 실패: { "success": false, "error": { "code": "...", "message": "..." } }
 *
 * 주의: record 컴포넌트(success, error)는 같은 이름의 accessor 메서드를 자동 생성한다.
 * 그래서 팩토리 메서드 이름은 컴포넌트명과 겹치지 않게 ok/fail 로 둔다.
 */
public record ApiResponse<T>(boolean success, T data, ErrorBody error) {

    /** 데이터가 있는 성공 응답 */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /** 데이터가 없는 성공 응답(예: 재발급/로그아웃) */
    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null);
    }

    /** 실패 응답. code는 기계용 식별자, message는 사용자 노출용. */
    public static ApiResponse<Void> fail(String code, String message) {
        return new ApiResponse<>(false, null, new ErrorBody(code, message));
    }

    public record ErrorBody(String code, String message) {}
}