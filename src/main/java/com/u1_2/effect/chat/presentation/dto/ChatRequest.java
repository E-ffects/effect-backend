package com.u1_2.effect.chat.presentation.dto;

import com.u1_2.effect.chat.domain.ChatScope;
import com.u1_2.effect.chat.domain.ChatScopeType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/** thread_id가 없으면 새 대화로 보고, 응답의 thread_id를 다음 질문에 보냄 */
public record ChatRequest(
        @NotBlank @Size(max = 1000) String message,
        @NotNull @Valid Scope scope,
        UUID threadId
) {
    public record Scope(@NotNull ChatScopeType type, @NotBlank @Size(max = 64) String id, LocalDate from, LocalDate to) {
        public ChatScope toScope() {
            return new ChatScope(type, id.trim(), from, to);
        }
    }
}
