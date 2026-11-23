package com.u1_2.effect.recurrence.presentation.dto;

import com.u1_2.effect.recurrence.domain.RecurrenceDecision;
import java.time.OffsetDateTime;

public record RecurrenceDecisionResponse(String rmaId, RecurrenceDecision decision, String decidedBy, OffsetDateTime decidedAt) {
}
