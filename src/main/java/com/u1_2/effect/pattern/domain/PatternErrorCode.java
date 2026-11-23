package com.u1_2.effect.pattern.domain;

import com.u1_2.effect.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PatternErrorCode implements ErrorCode {
    PATTERN_NOT_FOUND(HttpStatus.NOT_FOUND, "PATTERN_NOT_FOUND", "불량 패턴을 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String errorCode;
    private final String message;
}
