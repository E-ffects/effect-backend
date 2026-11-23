package com.u1_2.effect.recurrence.presentation.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record CorrectiveActionTrendResponse(
        String correctiveActionId, String investigationId, OffsetDateTime implementedAt, LocalDate completedDt,
        List<Point> points, Double beforeAvg, Double afterAvg, String recentTrend, List<Check> checks
) {
    public record Point(LocalDate from, LocalDate to, String label, long count, boolean after, boolean recurrence) {
    }

    public record Check(
            String checkId, String metric, Double baselineValue, Double targetValue, Double postValue,
            OffsetDateTime windowFrom, OffsetDateTime windowTo, String result, OffsetDateTime checkedAt
    ) {
    }
}
