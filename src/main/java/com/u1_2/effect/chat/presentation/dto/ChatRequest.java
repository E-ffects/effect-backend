package com.u1_2.effect.chat.presentation.dto;

import com.u1_2.effect.chat.domain.ChatScope;
import com.u1_2.effect.chat.domain.ChatScopeType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
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
    /** id는 RMA·PATTERN에서만 필수. GENERAL은 보내도 쓰지 않음 */
    public record Scope(@NotNull ChatScopeType type, @Size(max = 64) String id, LocalDate from, LocalDate to) {

        @AssertTrue
        public boolean isIdPresent() {
            return type == null || !type.requiresId() || (id != null && !id.isBlank());
        }

        public ChatScope toScope() {
            return ChatScope.of(type, id, from, to);
        }
    }
}
