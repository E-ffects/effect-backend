package com.u1_2.effect.report.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EditReportRequest(
        @NotBlank @Size(max = 10000) String mainCause,
        @NotBlank @Size(max = 10000) String qualityRecommendation,
        @NotBlank @Size(max = 10000) String csRecommendation
) {
}
