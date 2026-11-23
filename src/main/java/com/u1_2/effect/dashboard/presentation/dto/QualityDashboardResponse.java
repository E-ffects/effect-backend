package com.u1_2.effect.dashboard.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;

public record QualityDashboardResponse(
        Period period, Kpis kpis, List<Defect> defectTop5, List<Repeat> repeatRank, List<Priority> priority
) {
    public record Period(LocalDate from, LocalDate to) {
    }

    public record Kpis(
            Comparison totalRma, Repeats repeatPatterns, Recurrence recurrenceSuspected,
            Comparison loss, Rate causeConfirmRate, Rate countermeasureEffectRate
    ) {
    }

    public record Comparison(long value, Double changePct) {
    }

    public record Repeats(long value, @JsonProperty("new") long newlyRepeated) {
    }

    public record Recurrence(long value) {
    }

    public record Rate(Double value, long num, long den) {
    }

    public record Defect(String defectType, long count) {
    }

    public record Repeat(
            String investigationId, String title, String product, String defectType, String primaryCause,
            long count, long prevCount, String rmaId
    ) {
    }

    public record Priority(
            String investigationId, String title, String analysisLevel, String product, String defectType,
            long count, long defectQty, long loss, String topSeverity, long prevCount, boolean highRisk,
            double score, String urgency, List<ScorePart> breakdown, String rmaId, List<String> evidence
    ) {
    }

    public record ScorePart(String key, String label, double weight, double score, String detail) {
    }
}
