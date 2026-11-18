package com.u1_2.effect.global.exception;

import lombok.Getter;

/**
 * 비즈니스 예외. 서비스에서 {@code throw new ApiException(RmaErrorCode.RMA_NOT_FOUND)}로 던진다.
 */
@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
