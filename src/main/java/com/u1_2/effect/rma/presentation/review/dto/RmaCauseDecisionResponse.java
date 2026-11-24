package com.u1_2.effect.rma.presentation.review.dto;

import java.time.OffsetDateTime;

public record RmaCauseDecisionResponse(String rmaId, String approvalStatus, String decidedBy, OffsetDateTime decidedAt) {
}
