package com.u1_2.effect.recurrence.presentation.dto;

import com.u1_2.effect.recurrence.domain.RecurrenceDecision;
import jakarta.validation.constraints.NotNull;

public record DecideRecurrenceRequest(@NotNull RecurrenceDecision decision) {
}
