package com.u1_2.effect.rma.presentation.intake.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public record RmaIntakeResponse(
        String rmaId, String intakeState, String csStatus, String qaStatus, Symptom symptom, Triage triage,
        List<String> followUpQuestions, Analysis analysis, List<Attachment> attachments,
        @JsonInclude(JsonInclude.Include.NON_NULL) IntakeErrorResponse error
) {
    public record Symptom(String primaryLabel, String minorName, String majorName, String status, List<String> evidenceSpans) {
    }

    public record Triage(int severity, String characteristicSymbol, String analysisLevel, boolean needsApproval,
                         List<String> approvalReasons, String rationale,
                         @JsonInclude(JsonInclude.Include.NON_NULL) Boolean provisional) {
    }

    public record Analysis(String analysisId, String analysisState) {
    }

    public record Attachment(long attachmentId, String kind, String fileName) {
    }
}
