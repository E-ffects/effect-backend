package com.u1_2.effect.chat.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** 새로고침 후 이어 보기용 열린 대화 */
public record ChatThreadResponse(UUID threadId, String scopeType, String scopeId, List<Message> messages) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Message(String role, String text, String status, JsonNode evidence, JsonNode suggestions,
                          OffsetDateTime createdAt) {
    }
}
