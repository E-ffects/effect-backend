package com.u1_2.effect.rma.presentation.communication.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record RmaServiceActionHistoryResponse(String rmaId, List<Item> items, long total, int page, int size) {

    public record Item(
            String actionId, String rmaItemId, String actionType, String policyVersion, long boardQty,
            String result, OffsetDateTime decidedAt, OffsetDateTime executedAt
    ) {
    }
}
