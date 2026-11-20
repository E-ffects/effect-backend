package com.u1_2.effect.rma.presentation.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 임시 QA 목록과 승인 목록에 공통 페이지 구조를 제공함.
 */
public record RmaReviewResponse<T>(List<T> items, long total, int page, int size) {

    public record Approval(String rmaId, String analysisId, String analysisLevel, List<String> approvalReasons,
                           String topCauseCode, String topCauseName, OffsetDateTime requestedAt, String approvalStatus,
                           Integer severity, String rationale, String decidedBy) {
    }

    public record Triage(String rmaId, OffsetDateTime openedAt, String customerName, String reportedPartNo,
                         String minorName, Proposal proposal) {
    }

    public record Proposal(int severity, String analysisLevel, String rationale) {
    }
}
