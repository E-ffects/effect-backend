package com.u1_2.effect.rma.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record RmaSimilarResponse(String rmaId, OffsetDateTime asOf, List<Item> items) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Item(
            String rmaId, OffsetDateTime openedAt, LocalDate claimDate, String minorCode, String minorName,
            double similarity, Integer daysToResolve, Boolean recurrence, String basis, String pastAction,
            String causeCode, String causeName, String processId, String equipmentId, String certainty,
            String snippet, List<Action> correctiveActions
    ) {
    }

    public record Action(String category, String description, OffsetDateTime implementedAt) {
    }
}
