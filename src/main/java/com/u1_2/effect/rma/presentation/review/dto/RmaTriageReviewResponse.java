package com.u1_2.effect.rma.presentation.review.dto;

import java.time.OffsetDateTime;

public record RmaTriageReviewResponse(
        String rmaId, OffsetDateTime openedAt, String customerName, String reportedPartNo,
        String minorName, Proposal proposal
) {
    public record Proposal(int severity, String analysisLevel, String rationale) {
    }
}
