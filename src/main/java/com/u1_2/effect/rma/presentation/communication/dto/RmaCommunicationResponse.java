package com.u1_2.effect.rma.presentation.communication.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record RmaCommunicationResponse(String rmaId, String messageId, String csStatus, OffsetDateTime sentAt) {

    public record History(List<Item> items) {
    }

    public record Item(String messageId, String audience, String authorRole, String authorName, String messageText,
                       OffsetDateTime sentAt) {
    }
}
