package com.u1_2.effect.patternreport.presentation.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreatePatternReportRequest(LocalDate from, LocalDate to, @Size(max = 100) String product) {
}
