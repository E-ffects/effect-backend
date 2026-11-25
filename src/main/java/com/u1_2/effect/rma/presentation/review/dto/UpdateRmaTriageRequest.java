package com.u1_2.effect.rma.presentation.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 품질관리자가 직접 수정할 수 있는 필드만 받음. 수정자 정보는 인증으로 확인함.
 */
public record UpdateRmaTriageRequest(
        @NotNull @Min(0) @Max(10) Integer severity,
        String characteristicSymbol,
        @NotNull Level analysisLevel,
        @NotBlank String rationale
) {
    public enum Level { L1, L2, L3 }
}
