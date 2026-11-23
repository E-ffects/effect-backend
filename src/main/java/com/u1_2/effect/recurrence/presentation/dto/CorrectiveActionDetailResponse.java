package com.u1_2.effect.recurrence.presentation.dto;

import java.time.OffsetDateTime;
import tools.jackson.databind.JsonNode;

public record CorrectiveActionDetailResponse(
        String correctiveActionId, String investigationId, String actionRole, String actionCategory,
        String description, String ownerDept, String causeCode, String escapeCode,
        OffsetDateTime selectedAt, OffsetDateTime dueAt, OffsetDateTime implementedAt,
        String verificationStatus, JsonNode scope
) {
}
