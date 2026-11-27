package com.u1_2.effect.user.domain;

import com.u1_2.effect.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
    DUPLICATE_USERNAME(HttpStatus.CONFLICT, "DUPLICATE_USERNAME", "이미 사용 중인 아이디입니다."),
    APPROVAL_PENDING(HttpStatus.FORBIDDEN, "APPROVAL_PENDING", "관리자 승인 대기 중인 계정입니다."),
    APPROVAL_REJECTED(HttpStatus.FORBIDDEN, "APPROVAL_REJECTED", "가입이 반려된 계정입니다."),
    ADMIN_REQUIRED(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "관리자 권한이 필요합니다.");

    private final HttpStatus httpStatus;
    private final String errorCode;
    private final String message;
}
