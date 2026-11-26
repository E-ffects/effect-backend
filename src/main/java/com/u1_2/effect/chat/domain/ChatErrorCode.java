package com.u1_2.effect.chat.domain;

import com.u1_2.effect.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ChatErrorCode implements ErrorCode {
    // 다른 사용자의 대화도 같은 응답을 줘 존재 여부를 드러내지 않음
    CHAT_THREAD_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT_THREAD_NOT_FOUND", "대화를 찾을 수 없습니다. 새 대화로 다시 질문해 주세요."),
    CHAT_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "CHAT_RATE_LIMITED", "질문이 너무 많습니다. 잠시 후 다시 질문해 주세요.");

    private final HttpStatus httpStatus;
    private final String errorCode;
    private final String message;
}
