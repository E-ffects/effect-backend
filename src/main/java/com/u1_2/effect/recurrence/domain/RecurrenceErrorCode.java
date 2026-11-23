package com.u1_2.effect.recurrence.domain;

import com.u1_2.effect.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum RecurrenceErrorCode implements ErrorCode {
    CORRECTIVE_ACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "CORRECTIVE_ACTION_NOT_FOUND", "대책을 찾을 수 없습니다."),
    INVESTIGATION_NOT_FOUND(HttpStatus.NOT_FOUND, "INVESTIGATION_NOT_FOUND", "조사를 찾을 수 없습니다."),
    CORRECTIVE_ACTION_LOCKED(HttpStatus.CONFLICT, "CORRECTIVE_ACTION_LOCKED", "시행 또는 검토 이력이 있는 대책입니다."),
    CORRECTIVE_ACTION_REQUEST_CONFLICT(HttpStatus.CONFLICT, "CORRECTIVE_ACTION_REQUEST_CONFLICT", "이미 다른 내용으로 사용된 요청 키입니다."),
    RECURRENCE_ALREADY_DECIDED(HttpStatus.CONFLICT, "RECURRENCE_ALREADY_DECIDED", "이미 재발 여부가 확정된 접수입니다.");

    private final HttpStatus httpStatus;
    private final String errorCode;
    private final String message;
}
