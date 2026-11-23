package com.u1_2.effect.report.domain;

import com.u1_2.effect.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReportErrorCode implements ErrorCode {
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND", "보고서가 없습니다"),
    REPORT_ANALYSIS_NOT_READY(HttpStatus.CONFLICT, "REPORT_ANALYSIS_NOT_READY", "저장된 분석 결과를 먼저 확인하세요."),
    REPORT_DRAFT_STALE(HttpStatus.CONFLICT, "REPORT_DRAFT_STALE", "현재 분석을 바탕으로 초안을 다시 생성하세요.");

    private final HttpStatus httpStatus;
    private final String errorCode;
    private final String message;
}
