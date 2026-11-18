package com.u1_2.effect.global.exception;

import org.springframework.http.HttpStatus;

public interface ErrorCode {

    HttpStatus getHttpStatus();

    String getErrorCode();

    String getMessage();
}
