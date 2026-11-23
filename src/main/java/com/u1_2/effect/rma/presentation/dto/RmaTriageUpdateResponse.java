package com.u1_2.effect.rma.presentation.dto;

import java.time.OffsetDateTime;

public record RmaTriageUpdateResponse(String rmaId, String analysisLevel, String qaStatus, String intakeState,
                                      String decidedBy, OffsetDateTime decidedAt) {
}
