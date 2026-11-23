package com.u1_2.effect.pattern.presentation.dto;

import java.time.LocalDate;
import java.util.List;

public record PatternListResponse(
        Period period, List<Pattern> patterns, List<Cause> causes, int total, int page, int size
) {
    public record Period(LocalDate from, LocalDate to) {
    }

    public record Pattern(
            String patternId, String investigationId, String title, String product, String defectType,
            String investigationGrade, String status, long count, double score, String primaryCause
    ) {
    }

    public record Cause(String cause, long count) {
    }
}
