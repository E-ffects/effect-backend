package com.u1_2.effect.recurrence.presentation.dto;

import com.u1_2.effect.recurrence.domain.CorrectiveActionPlan.ActionCategory;
import com.u1_2.effect.recurrence.domain.CorrectiveActionPlan.ActionRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import tools.jackson.databind.JsonNode;

public record SaveCorrectiveActionRequest(
        @NotNull ActionRole actionRole,
        @NotNull ActionCategory actionCategory,
        @NotBlank @Size(max = 4000) String description,
        @NotBlank @Size(max = 100) String ownerDept,
        @Size(max = 100) String causeCode,
        @Size(max = 100) String escapeCode,
        @NotNull OffsetDateTime dueAt,
        JsonNode scope
) {
}
