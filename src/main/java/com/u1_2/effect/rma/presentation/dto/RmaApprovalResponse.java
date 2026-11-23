package com.u1_2.effect.rma.presentation.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record RmaApprovalResponse(
        String rmaId, String analysisId, String analysisLevel, List<String> approvalReasons,
        String topCauseCode, String topCauseName, OffsetDateTime requestedAt, String approvalStatus,
        Integer severity, String rationale, String decidedBy
) {
}
