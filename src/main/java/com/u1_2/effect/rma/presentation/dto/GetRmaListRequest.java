package com.u1_2.effect.rma.presentation.dto;

import jakarta.validation.constraints.Size;

public record GetRmaListRequest(
        Integer page,
        Integer size,
        @Size(max = 50) String open
) {
}
