package com.backend_catcheat.global.exception;

import lombok.Getter;

/**
 * 도메인 로직에서 ErrorCode를 담아 던진다.
 */
@Getter
public class CustomException extends RuntimeException {

    private final ErrorCode errorCode;

    public CustomException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

}
