package com.u1_2.effect.recurrence.presentation.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record CorrectiveActionsResponse(List<Item> items, long total, int page, int size) {

    public record Item(
            String correctiveActionId, String investigationId, String investigationTitle, String actionRole,
            String actionCategory, String description, String ownerDept, String causeCode, OffsetDateTime selectedAt,
            OffsetDateTime dueAt, OffsetDateTime implementedAt, String verificationStatus, long recurrenceCount
    ) {
    }
}
