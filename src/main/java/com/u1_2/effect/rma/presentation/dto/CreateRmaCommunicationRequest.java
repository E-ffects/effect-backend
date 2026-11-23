package com.u1_2.effect.rma.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateRmaCommunicationRequest(@NotBlank String messageText, @NotNull Source source, String analysisId) {

    public enum Source {
        AI_DRAFT, AI_DRAFT_EDITED, MANUAL
    }
}
