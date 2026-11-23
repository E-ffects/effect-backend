package com.u1_2.effect.dashboard.presentation.dto;

import java.time.LocalDate;
import java.util.List;

public record DashboardTrendResponse(List<Point> points, List<Countermeasure> countermeasures) {

    public record Point(LocalDate from, LocalDate to, String label, long count) {
    }

    public record Countermeasure(LocalDate date, String label) {
    }
}
