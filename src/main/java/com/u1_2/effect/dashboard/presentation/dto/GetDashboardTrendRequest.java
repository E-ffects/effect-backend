package com.u1_2.effect.dashboard.presentation.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public record GetDashboardTrendRequest(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @Size(max = 100) String product,
        @Size(max = 100) String defectType
) {
}
