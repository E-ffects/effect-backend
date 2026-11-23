package com.u1_2.effect.rma.presentation.dto;

import java.time.OffsetDateTime;

public record RmaServiceActionResponse(String rmaId, String actionId, String actionType, String result, String csStatus,
                                       OffsetDateTime executedAt) {
}
