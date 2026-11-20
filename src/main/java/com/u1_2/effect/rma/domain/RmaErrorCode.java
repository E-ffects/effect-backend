package com.u1_2.effect.rma.domain;

import com.u1_2.effect.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 접수·조회·QA 상태에서 발생하는 오류를 HTTP 상태와 응답 코드로 정리함.
 */
@Getter
@RequiredArgsConstructor
public enum RmaErrorCode implements ErrorCode {
    RMA_NOT_FOUND(HttpStatus.NOT_FOUND, "RMA_NOT_FOUND", "RMA를 찾을 수 없습니다."),
    ATTACHMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "ATTACHMENT_NOT_FOUND", "첨부 파일을 찾을 수 없습니다."),
    INTAKE_NOT_FAILED(HttpStatus.CONFLICT, "INTAKE_NOT_FAILED", "실패 상태인 접수만 재처리할 수 있습니다."),
    CAUSE_ALREADY_DECIDED(HttpStatus.CONFLICT, "CAUSE_ALREADY_DECIDED", "원인이 확정된 접수입니다."),
    TRIAGE_LOCKED(HttpStatus.CONFLICT, "TRIAGE_LOCKED", "원인 판정이 끝난 접수의 QA 판정을 수정할 수 없습니다."),
    LOT_NOT_FOUND(HttpStatus.BAD_REQUEST, "LOT_NOT_FOUND", "제품과 일치하는 생산 Lot을 찾을 수 없습니다."),
    RAW_TEXT_EMPTY(HttpStatus.BAD_REQUEST, "RAW_TEXT_EMPTY", "원문 파일에서 텍스트를 확인할 수 없습니다."),
    UNSUPPORTED_FILE(HttpStatus.BAD_REQUEST, "UNSUPPORTED_FILE", "지원하지 않는 첨부 형식 또는 크기입니다.");

    private final HttpStatus httpStatus;
    private final String errorCode;
    private final String message;
}
