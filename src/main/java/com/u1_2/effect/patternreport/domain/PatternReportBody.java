package com.u1_2.effect.patternreport.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record PatternReportBody(
        String analysisLevel, String investigationStatus, Narrative narrative, Totals totals, List<MonthCount> monthly,
        List<ProductCount> products, List<LotCount> lots, List<EquipmentCount> equipment,
        List<Assessment> assessments, List<Finding> findings, List<ProcessStep> processFlow,
        List<Material> materials, List<Action> actions, List<EvidenceCase> evidenceCases
) {
    public record Narrative(String conclusion, String trend) {
    }

    public record Totals(long claims, long lots, long claimedQuantity, Long verifiedQuantity,
                         long investigationLossKrw, long unresolvedClaims) {
    }

    public record MonthCount(String month, long claims) {
    }

    public record ProductCount(String partNo, long claims, long lots) {
    }

    public record LotCount(String lot, long count) {
    }

    public record EquipmentCount(String equipmentId, long count) {
    }

    public record Assessment(
            String kind, String code, String name, String status, String processId, String equipmentId
    ) {
    }

    public record Finding(String kind, String sourceType, String summary) {
    }

    public record ProcessStep(String processId, String name, int sequence, boolean inspection,
                              boolean defectOccurrence, boolean detectionEscape) {
    }

    public record Material(String materialLotId, String materialType, String supplier, String inspectionResult,
                           long affectedLots) {
    }

    public record Action(
            String actionId, String department, String description, OffsetDateTime dueAt,
            OffsetDateTime implementedAt, String verificationStatus, List<EffectivenessCheck> checks
    ) {
    }

    public record EffectivenessCheck(
            String metric, BigDecimal baseline, BigDecimal target, BigDecimal measured,
            OffsetDateTime windowTo, String result
    ) {
    }

    public record EvidenceCase(
            String rmaId, LocalDate openedOn, String partNo, String detectionStage, String qaStatus,
            String membershipReason, String selectionReason, List<EvidenceItem> items
    ) {
    }

    public record EvidenceItem(
            String lotId, String symptom, long claimedQuantity, long returnedQuantity, Long verifiedQuantity,
            List<Measurement> measurements, List<ProcessObservation> observations
    ) {
    }

    public record Measurement(String name, BigDecimal value, String unit, String result) {
    }

    public record ProcessObservation(
            String processId, String equipmentId, String itemCode, BigDecimal value, String unit,
            BigDecimal lowerLimit, BigDecimal upperLimit, String spcFlag
    ) {
    }
}
