package com.u1_2.effect.report.presentation.dto;

import com.u1_2.effect.report.domain.ReportBody;
import jakarta.validation.constraints.NotNull;

public record ReplaceReportRequest(@NotNull ReportBody content) {
}
