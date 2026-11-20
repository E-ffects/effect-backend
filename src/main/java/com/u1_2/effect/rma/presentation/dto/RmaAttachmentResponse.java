package com.u1_2.effect.rma.presentation.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 접수에 연결된 첨부 유형과 이름, 형식, 크기, 업로드 시각을 전달함.
 */
public record RmaAttachmentResponse(List<Attachment> items) {

    public record Attachment(long attachmentId, String kind, String fileName, String contentType, long size,
                             OffsetDateTime uploadedAt) {
    }
}
