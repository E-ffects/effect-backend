package com.u1_2.effect.recurrence.presentation.dto;

import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.BindParam;

public record GetRecurrenceReviewsRequest(
        @BindParam("corrective_action_id") @Size(max = 100) String correctiveActionId,
        @Size(max = 100) String verdict, Integer page, Integer size
) {
}
