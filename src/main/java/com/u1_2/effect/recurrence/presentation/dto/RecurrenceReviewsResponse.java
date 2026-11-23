package com.u1_2.effect.recurrence.presentation.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record RecurrenceReviewsResponse(List<Item> items, long total, int page, int size) {

    public record Item(
            String reviewId, String correctiveActionId, String rmaId, String rmaItemId, boolean lotProducedAfterAction,
            boolean symptomMatch, boolean causeMatch, String verdict, OffsetDateTime reviewedAt
    ) {
    }
}
