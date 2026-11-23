package com.u1_2.effect.pattern.presentation.dto;

import java.util.List;

public record PatternReportResponse(
        String patternId, String title, String mainCause, String status, String recommendation,
        List<String> evidence, String disclaimer
) {
}
