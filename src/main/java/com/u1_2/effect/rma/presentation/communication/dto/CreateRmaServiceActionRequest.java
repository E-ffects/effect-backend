package com.u1_2.effect.rma.presentation.communication.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record CreateRmaServiceActionRequest(
        @NotNull ActionType actionType, @NotNull @PositiveOrZero Long boardQty,
        @NotNull Result result, String source
) {
    public enum ActionType {
        REPLACEMENT_SHIPMENT, CREDIT_NOTE, SORTING_SUPPORT_AND_CREDIT, NO_CHARGE_SAMPLE_RETURN
    }

    public enum Result {
        SCHEDULED, EXECUTED
    }
}
