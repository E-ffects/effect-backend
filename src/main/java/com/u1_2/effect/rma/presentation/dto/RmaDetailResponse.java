package com.u1_2.effect.rma.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.u1_2.effect.recurrence.domain.RecurrenceDecision;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 접수 상세 화면의 응답임. CS와 QUALITY는 볼 수 있는 QA 정보 범위가 다름.
 */
public record RmaDetailResponse(
        String rmaId,
        OffsetDateTime openedAt,
        OffsetDateTime occurredAt,
        String rawText,
        String reportedPartNo,
        String reportedLotNo,
        String reportedDateCode,
        String detectionStage,
        String csStatus,
        Customer customer,
        List<Item> items,
        Symptom symptom,
        Triage triage,
        @JsonInclude(JsonInclude.Include.NON_NULL) TriageProposal triageProposal,
        @JsonInclude(JsonInclude.Include.NON_NULL) String qaStatus,
        @JsonInclude(JsonInclude.Include.NON_NULL) String approvalStatus,
        String intakeState,
        String analysisState,
        @JsonInclude(JsonInclude.Include.NON_NULL) ReportStatus report,
        @JsonInclude(JsonInclude.Include.NON_NULL) RecurrenceDecision recurrenceDecision,
        @JsonInclude(JsonInclude.Include.NON_NULL) String decidedBy,
        @JsonInclude(JsonInclude.Include.NON_NULL) OffsetDateTime decidedAt,
        boolean followUpDraftAvailable
) {
    public record ReportStatus(boolean exists, boolean reviewed) {
    }

    public record Customer(String customerId, String customerName, String industry) {
    }

    public record Item(
            String rmaItemId,
            String panelId,
            long inputQty,
            long claimedDefectQty,
            long returnedQty,
            Long verifiedDefectQty,
            @JsonInclude(JsonInclude.Include.NON_NULL) String lotId
    ) {
    }

    public record Symptom(
            String primaryLabel, String minorName, String majorName, String status, List<String> evidenceSpans
    ) {
    }

    public sealed interface Triage permits CustomerTriage, QualityTriage {
    }

    public record CustomerTriage(String analysisLevel) implements Triage {
    }

    public record QualityTriage(
            int severity,
            String characteristicSymbol,
            String analysisLevel,
            String rationale,
            String decidedBy,
            OffsetDateTime decidedAt
    ) implements Triage {
    }

    public record TriageProposal(
            int severity, String characteristicSymbol, String analysisLevel, String rationale, boolean provisional
    ) {
    }
}
