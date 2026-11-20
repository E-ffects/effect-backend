package com.u1_2.effect.rma.presentation.dto;

/**
 * 새로 등록된 분석과 재사용된 분석에 같은 응답 구조를 사용함.
 */
public record RmaAnalysisRegistrationResponse(String rmaId, String analysisId, String analysisState) {
}
