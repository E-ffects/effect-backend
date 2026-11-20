package com.u1_2.effect.rma.domain;

import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * v5 원본에 접수·품목을 쓰고 같은 트랜잭션에서 Spring 전용 상태를 남김.
 */
@Repository
@RequiredArgsConstructor
public class RmaIntakeRepository {

    private final JdbcClient jdbcClient;

    public void lockRegistration() {
        // 같은 DB에서 접수 번호를 동시에 계산하지 않도록 트랜잭션이 끝날 때까지 잠금
        jdbcClient.sql("SELECT pg_advisory_xact_lock(14531)")
                .query((row, index) -> true).single();
    }

    public Optional<String> findRegisteredId(long userId, String key) {
        return jdbcClient.sql("""
                SELECT c.rma_id
                FROM pcb.rma_claim c
                JOIN app.intake i ON i.rma_id = c.rma_id
                WHERE i.created_by = :userId AND c.idempotency_key = :key
                """).param("userId", userId).param("key", key).query(String.class).optional();
    }

    public long nextNumber() {
        // 기존 v5 접수와 품목 번호 중 큰 값 다음부터 새 번호를 발급함
        return jdbcClient.sql("""
                SELECT GREATEST(
                    COALESCE((SELECT MAX(SUBSTRING(rma_id FROM 5)::BIGINT)
                              FROM pcb.rma_claim WHERE rma_id ~ '^RMA-[0-9]+$'), 0),
                    COALESCE((SELECT MAX(SUBSTRING(rma_item_id FROM 6)::BIGINT)
                              FROM pcb.rma_item WHERE rma_item_id ~ '^ITEM-[0-9]+$'), 0)
                ) + 1
                """).query(Long.class).single();
    }

    public boolean customerExists(String customerId) {
        return jdbcClient.sql("SELECT EXISTS (SELECT 1 FROM pcb.customer WHERE customer_id = :customerId)")
                .param("customerId", customerId).query(Boolean.class).single();
    }

    public boolean productExists(String partNo) {
        return jdbcClient.sql("SELECT EXISTS (SELECT 1 FROM pcb.product_spec WHERE part_no = :partNo)")
                .param("partNo", partNo).query(Boolean.class).single();
    }

    public boolean lotMatchesPart(String lotId, String partNo) {
        return jdbcClient.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM pcb.production_lot l
                    JOIN pcb.product_spec p ON p.spec_id = l.spec_id
                    WHERE l.lot_id = :lotId AND p.part_no = :partNo
                )
                """).param("lotId", lotId).param("partNo", partNo).query(Boolean.class).single();
    }

    public void insert(IntakeRow row) {
        jdbcClient.sql("""
                INSERT INTO pcb.rma_claim (rma_id, external_claim_id, customer_id, opened_at, occurred_at,
                    raw_text, reported_part_no, reported_lot_no, reported_date_code, detection_stage,
                    cs_status, qa_status, idempotency_key)
                VALUES (:rmaId, :rmaId, :customerId, :openedAt, :occurredAt, :rawText, :partNo, :lotId,
                    :dateCode, :detectionStage, 'OPEN', 'RECEIVED', :idempotencyKey)
                """).paramSource(row).update();
        jdbcClient.sql("""
                INSERT INTO pcb.rma_item (rma_item_id, rma_id, panel_id, lot_id, shipment_lot_id, input_qty,
                    claimed_defect_qty, returned_qty, verified_defect_qty, trace_status)
                VALUES (:itemId, :rmaId, :panelId, :lotId, NULL, :inputQty, :claimedDefectQty, 0, NULL, :traceStatus)
                """).paramSource(row).update();
        jdbcClient.sql("""
                INSERT INTO app.intake (rma_id, intake_state, customer_request, created_by, created_at, updated_at)
                VALUES (:rmaId, 'PROCESSING', :customerRequest, :userId, :createdAt, :createdAt)
                """).paramSource(row).update();
    }

    public void insertAttachment(AttachmentRow row) {
        jdbcClient.sql("""
                INSERT INTO app.attachment (rma_id, kind, file_name, content_type, size, storage_path,
                    uploaded_by, uploaded_at)
                VALUES (:rmaId, :kind, :fileName, :contentType, :size, :storagePath, :userId, :uploadedAt)
                """).paramSource(row).update();
    }

    public record IntakeRow(
            String rmaId, String itemId, String customerId, String openedAt, String occurredAt, String rawText,
            String partNo, String lotId, String dateCode, String detectionStage, String idempotencyKey,
            String panelId, long inputQty, long claimedDefectQty, String traceStatus, String customerRequest,
            long userId, OffsetDateTime createdAt
    ) {
    }

    public record AttachmentRow(
            String rmaId, String kind, String fileName, String contentType, long size, String storagePath,
            long userId, OffsetDateTime uploadedAt
    ) {
    }
}
