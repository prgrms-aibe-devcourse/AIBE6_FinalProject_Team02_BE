package com.backend_catcheat.spike.vision.controller;

import com.backend_catcheat.global.common.ApiResponse;
import com.backend_catcheat.spike.vision.exception.VisionSpikeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Set;

@RestControllerAdvice(assignableTypes = VisionSpikeController.class)
public class VisionSpikeExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(VisionSpikeExceptionHandler.class);

    private static final Set<String> SERVER_ERROR_CODES =
            Set.of("AI_CALL_FAILED", "AI_KEY_MISSING", "AI_RESPONSE_PARSE_FAILED", "AI_RESPONSE_EMPTY", "IMAGE_ENCODE_FAILED");

    @ExceptionHandler(VisionSpikeException.class)
    public ResponseEntity<ApiResponse<Void>> handleVisionSpike(VisionSpikeException e) {
        HttpStatus status = SERVER_ERROR_CODES.contains(e.getCode())
                ? HttpStatus.INTERNAL_SERVER_ERROR
                : HttpStatus.BAD_REQUEST;

        if (status.is5xxServerError()) {
            log.error("[spike] {} - {}", e.getCode(), e.getMessage(), e);
        } else {
            log.warn("[spike] {} - {}", e.getCode(), e.getMessage());
        }

        return ResponseEntity.status(status).body(ApiResponse.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("[spike] 업로드 용량 초과", e);
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(ApiResponse.fail("IMAGE_UPLOAD_TOO_LARGE", "사진은 장당 10MB까지 올릴 수 있어요"));
    }
}
