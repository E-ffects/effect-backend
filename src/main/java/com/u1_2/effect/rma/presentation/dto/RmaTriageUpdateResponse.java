package com.u1_2.effect.rma.presentation.dto;

import java.time.OffsetDateTime;

/**
 * 판정 저장 및 분석 등록 이후의 최신 상태와 판정자 정보를 전달함.
 */
public record RmaTriageUpdateResponse(String rmaId, String analysisLevel, String qaStatus, String intakeState,
                                      String decidedBy, OffsetDateTime decidedAt) {
}
