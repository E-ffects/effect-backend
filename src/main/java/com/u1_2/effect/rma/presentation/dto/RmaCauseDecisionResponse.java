package com.u1_2.effect.rma.presentation.dto;

import java.time.OffsetDateTime;

public record RmaCauseDecisionResponse(String rmaId, String approvalStatus, String decidedBy, OffsetDateTime decidedAt) {
}
