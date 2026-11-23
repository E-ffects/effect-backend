package com.u1_2.effect.recurrence.presentation.dto;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public record ImplementCorrectiveActionRequest(@NotNull OffsetDateTime implementedAt) {
}
