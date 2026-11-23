package com.u1_2.effect.dashboard.presentation.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record CsDashboardResponse(Period period, OffsetDateTime asOf, Kpis kpis) {

    public record Period(LocalDate from, LocalDate to) {
    }

    public record Kpis(long receivedCount, long pendingCount, long closedCount, long estimatedLossKrw) {
    }
}
