package com.u1_2.effect.alert.presentation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.BindParam;

public record GetAlertsRequest(
        @Size(max = 30) String type,
        @Min(1) @Max(100) Integer limit,
        @BindParam("after_id") @PositiveOrZero Long afterId,
        Boolean starred
) {
}
