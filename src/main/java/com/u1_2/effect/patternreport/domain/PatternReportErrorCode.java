package com.u1_2.effect.patternreport.domain;

import com.u1_2.effect.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PatternReportErrorCode implements ErrorCode {
    PATTERN_REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "PATTERN_REPORT_NOT_FOUND", "패턴 보고서를 찾을 수 없습니다."),
    PATTERN_REPORT_NO_CASES(HttpStatus.CONFLICT, "PATTERN_REPORT_NO_CASES", "선택 범위에 보고서를 작성할 RMA가 없습니다.");

    private final HttpStatus httpStatus;
    private final String errorCode;
    private final String message;
}
