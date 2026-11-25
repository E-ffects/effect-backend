package com.u1_2.effect.rma.presentation.communication.dto;

/**
 * 2차 회신 초안. 화면이 문안을 편집한 뒤 기존 회신 기록 API에 analysisId와 source를 그대로 보냄.
 */
public record RmaFollowUpDraftResponse(String rmaId, String messageText, String analysisId, String source) {
}
