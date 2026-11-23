package com.u1_2.effect.rma.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;

public record RmaListResponse(
        List<Item> items, long total, long page, int size,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean openFound,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean openExists
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Item(
            String rmaId, OffsetDateTime openedAt, String customerName, String reportedPartNo,
            String detectionStage, String majorName, String minorName, String classificationStatus,
            long claimedDefectQty, Integer severity, String analysisLevel, String csStatus,
            String qaStatus, String intakeState, String analysisState, String approvalStatus, String recurrenceVerdict
    ) {
    }
}
