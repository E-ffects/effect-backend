package com.u1_2.effect.alert.presentation.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record AlertsResponse(List<Item> items, long unread, Map<String, Long> unreadByType, long maxId, long starredCount) {

    public record Item(
            long id, String type, String rmaId, String recurId, String patternId, String targetLabel,
            String reason, String checkItems, OffsetDateTime createdAt, boolean read, boolean starred
    ) {
    }

    public record Updated(int updated) {
    }

    public record Deleted(int deleted) {
    }
}
