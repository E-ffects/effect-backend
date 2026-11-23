package com.u1_2.effect.recurrence.presentation.dto;

import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.BindParam;

public record GetCorrectiveActionsRequest(
        @BindParam("investigation_id") @Size(max = 100) String investigationId, Integer page, Integer size
) {
}
