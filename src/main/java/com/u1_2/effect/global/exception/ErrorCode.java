package com.u1_2.effect.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 도메인별 오류 코드 enum이 구현한다 (CommonErrorCode, AuthErrorCode, RmaErrorCode ...).
 * errorCode는 이름 형식(RMA_NOT_FOUND)이며, 한 번 배포한 값은 바꾸지 않는다.
 */
public interface ErrorCode {

    HttpStatus getHttpStatus();

    String getErrorCode();

    String getMessage();
}
