package com.u1_2.effect.report.presentation.dto;

import com.u1_2.effect.report.domain.ReportBody;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record ReportDetailResponse(
        String rmaId, String product, String defectType, LocalDate claimDate, String severity, String claimStatus,
        OffsetDateTime generatedAt, boolean reviewed, String reviewedBy, OffsetDateTime reviewedAt, ReportBody content
) {
    public record Draft(String rmaId, OffsetDateTime generatedAt, ReportBody content) {
    }

    public record Review(boolean reviewed, String reviewedBy, OffsetDateTime reviewedAt) {
    }
}
