package com.backend_catcheat.global.common;


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