package com.u1_2.effect.dashboard.presentation.dto;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public record GetDashboardRequest(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
) {
}
