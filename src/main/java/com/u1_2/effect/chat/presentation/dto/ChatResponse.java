package com.u1_2.effect.chat.presentation.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ChatResponse(
        UUID threadId, String status, String answer, List<Evidence> evidence, List<Suggestion> suggestions,
        OffsetDateTime createdAt
) {
    /** rma_id가 있으면 화면이 그 RMA를 열 수 있음 */
    public record Evidence(String type, String rmaId, String label) {
    }

    public record Suggestion(String task, String label) {
    }
}
