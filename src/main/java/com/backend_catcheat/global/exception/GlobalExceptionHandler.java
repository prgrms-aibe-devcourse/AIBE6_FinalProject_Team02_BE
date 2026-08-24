package com.backend_catcheat.global.exception;

import com.backend_catcheat.global.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatusCode;

/**
 * 전역 예외 처리
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 올바르지 않은 요청 예외처리
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(ErrorCode.INVALID_INPUT.name(), e.getMessage()));
    }

    // 도메인 비즈니스 예외 —> ErrorCode의 status/message로 응답
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(CustomException e) {
        ErrorCode code = e.getErrorCode();
        return ResponseEntity.status(code.getStatus())
            .body(ApiResponse.fail(code.name(), code.getMessage()));
    }

    // 입력 검증 실패
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(err -> err.getDefaultMessage())
            .orElse(ErrorCode.INVALID_INPUT.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.fail(ErrorCode.INVALID_INPUT.name(), message));
    }

    // 쿼리 파라미터 누락
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException e) {
        String message = String.format("%s 값이 필요해요", e.getParameterName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.fail(ErrorCode.INVALID_INPUT.name(), message));
    }

    // 파라미터 타입 불일치
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String message = String.format("%s 값을 확인해 주세요", e.getName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.fail(ErrorCode.INVALID_INPUT.name(), message));
    }

    // 동시성: 방금 삭제된 챌린지를 참조하는 등 무결성 위반 → 친절한 409로 변환
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleIntegrity(DataIntegrityViolationException e) {
        log.warn("무결성 위반(동시성 등)", e);
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.fail(ErrorCode.CONCURRENT_MODIFICATION.name(), ErrorCode.CONCURRENT_MODIFICATION.getMessage()));
    }

    // ResponseStatusException(예: 재발급 401) — 상태코드를 그대로 살리고,
    // 4xx는 WARN, 5xx만 ERROR로 로깅한다(정상적인 클라이언트 오류가 서버 장애로 집계되지 않게).
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatus(ResponseStatusException e) {
        HttpStatusCode status = e.getStatusCode();
        String reason = e.getReason() != null ? e.getReason() : "요청을 처리할 수 없습니다.";
        if (status.is5xxServerError()) {
            log.error("서버 오류(ResponseStatusException) status={}", status.value(), e);
        } else {
            log.warn("클라이언트 오류 status={} reason={}", status.value(), reason);
        }
        String code = HttpStatus.resolve(status.value()) != null
                ? HttpStatus.valueOf(status.value()).name()
                : "REQUEST_FAILED";
        return ResponseEntity.status(status).body(ApiResponse.fail(code, reason));
    }

    // 예상 못한 예외 — 원인은 서버 로그에만 남김
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.fail(ErrorCode.INTERNAL_ERROR.name(), ErrorCode.INTERNAL_ERROR.getMessage()));
    }


}
