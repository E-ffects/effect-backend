package com.u1_2.effect.rma.presentation.intake.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateIntakeReplyRequest(@NotBlank @Size(max = 4000) String messageText) {
}
