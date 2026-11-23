package com.u1_2.effect.pattern.presentation.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record PatternDetailResponse(
        String patternId, String investigationId, String title, String status, String product, String defectType,
        String investigationGrade, Integer priorityRank, long unresolved, long loss, String primaryCause,
        Integer primaryRatio, String secondaryCause, Integer secondaryRatio, List<Lot> lots,
        List<Equipment> equipments, List<Evidence> evidence, List<Action> actions
) {
    public record Lot(String lot, long count) {
    }

    public record Equipment(String equipmentId, long count) {
    }

    public record Evidence(String rmaId, LocalDate claimDate, OffsetDateTime openedAt, String severity) {
    }

    public record Action(
            String correctiveActionId, String actionCategory, String description,
            String verificationStatus, OffsetDateTime implementedAt
    ) {
    }
}
