package com.u1_2.effect.rma.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 첨부 ID와 파일의 저장 키를 연결함. 목록에는 저장 경로를 포함하지 않음.
 */
@Repository
@RequiredArgsConstructor
public class RmaAttachmentRepository {

    private final JdbcClient jdbcClient;

    public List<Attachment> findAttachments(String rmaId) {
        return jdbcClient.sql("""
                SELECT id AS attachment_id, kind, file_name, content_type, size, uploaded_at
                FROM app.attachment WHERE rma_id = :rmaId ORDER BY id
                """).param("rmaId", rmaId).query(Attachment.class).list();
    }

    public Optional<StoredFile> findStoredFile(long id) {
        return jdbcClient.sql("""
                SELECT file_name, content_type, storage_path FROM app.attachment WHERE id = :id
                """).param("id", id).query(StoredFile.class).optional();
    }

    public record Attachment(long attachmentId, String kind, String fileName, String contentType, long size,
                             OffsetDateTime uploadedAt) {
    }

    public record StoredFile(String fileName, String contentType, String storagePath) {
    }
}
