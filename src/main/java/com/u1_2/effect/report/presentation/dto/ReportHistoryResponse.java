package com.u1_2.effect.report.presentation.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record ReportHistoryResponse(List<Item> items, long total, int page, int size) {

    public record Item(
            String rmaId, String product, String defectType, LocalDate claimDate, String severity,
            String claimStatus, OffsetDateTime generatedAt, boolean reviewed, String reviewedBy, OffsetDateTime reviewedAt
    ) {
    }
}
