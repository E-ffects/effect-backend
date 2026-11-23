package com.u1_2.effect.recurrence.domain;

import java.time.OffsetDateTime;
import tools.jackson.databind.JsonNode;

public record CorrectiveActionPlan(
        ActionRole actionRole, ActionCategory actionCategory, String description, String ownerDept,
        String causeCode, String escapeCode, OffsetDateTime dueAt, JsonNode scope
) {
    public enum ActionRole { OCCURRENCE, ESCAPE, HORIZONTAL, PREVENTION }

    public enum ActionCategory { INSPECTION, PROCESS_EQUIPMENT, PROCESS_LOCAL, SUPPLIER_SCAR, SYSTEM }
}
