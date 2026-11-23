package com.u1_2.effect.rma.presentation.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record RmaAttachmentResponse(List<Attachment> items) {

    public record Attachment(long attachmentId, String kind, String fileName, String contentType, long size,
                             OffsetDateTime uploadedAt) {
    }
}
