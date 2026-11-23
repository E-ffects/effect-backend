package com.u1_2.effect.config.ai;

import com.u1_2.effect.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * AI 서버 장애를 구분하는 코드임. 일반 DB·파일 오류에서는 503을 쓰지 않음.
 */
@Getter
@RequiredArgsConstructor
public enum AiErrorCode implements ErrorCode {
    AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI_UNAVAILABLE", "AI 서버에 연결할 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String errorCode;
    private final String message;
}
