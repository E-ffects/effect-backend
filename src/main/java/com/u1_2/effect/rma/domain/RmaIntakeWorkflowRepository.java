package com.u1_2.effect.rma.domain;

import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 분류·QA·분석 등록에 따라 접수 상태를 바꿈. 재처리 시 원본·첨부와 분석 이력은 유지함.
 */
@Repository
@RequiredArgsConstructor
public class RmaIntakeWorkflowRepository {

    private final JdbcClient jdbcClient;

    public boolean claimRetry(String rmaId, OffsetDateTime now) {
        // 실패에서 처리 중으로 바꾼 요청 한 건만 AI 재처리를 시작함
        return jdbcClient.sql("""
                UPDATE app.intake SET intake_state = 'PROCESSING', classification_json = NULL,
                    follow_up_questions_json = NULL, triage_result_json = NULL, triage_proposal_json = NULL,
                    error_code = NULL, error_detail = NULL, updated_at = :now
                WHERE rma_id = :rmaId AND intake_state = 'FAILED'
                """).param("rmaId", rmaId).param("now", now).update() == 1;
    }

    public boolean claimExists(String rmaId) {
        return jdbcClient.sql("SELECT EXISTS (SELECT 1 FROM pcb.rma_claim WHERE rma_id = :rmaId)")
                .param("rmaId", rmaId).query(Boolean.class).single();
    }

    public void clearAutomaticProposals(String rmaId) {
        // 재처리에서 다시 만들 자동 판정·미확인 분류만 비움
        jdbcClient.sql("""
                DELETE FROM pcb.symptom_assessment s USING pcb.rma_item i
                WHERE s.rma_item_id = i.rma_item_id AND i.rma_id = :rmaId
                    AND s.proposed_by = 'CS_AGENT' AND s.status = 'PROPOSED' AND s.confirmed_by IS NULL
                """).param("rmaId", rmaId).update();
        jdbcClient.sql("DELETE FROM pcb.qa_triage WHERE rma_id = :rmaId AND decided_by = 'QA_AGENT'")
                .param("rmaId", rmaId).update();
    }

    public Input findInput(String rmaId) {
        return jdbcClient.sql("""
                SELECT c.rma_id, i.rma_item_id, c.raw_text, c.detection_stage, c.reported_part_no, c.customer_id,
                       i.claimed_defect_qty
                FROM pcb.rma_claim c JOIN pcb.rma_item i ON i.rma_id = c.rma_id
                WHERE c.rma_id = :rmaId
                """).param("rmaId", rmaId).query(Input.class).single();
    }

    public int insertSymptom(String id, String itemId, String minorCode, String evidence, String createdAt) {
        // AI가 준 소분류 코드와 현재 DB 분류표 버전을 함께 보관함
        return jdbcClient.sql("""
                INSERT INTO pcb.symptom_assessment (assessment_id, rma_item_id, primary_label, intake_label,
                    label_version, secondary_labels_json, evidence_spans_json, proposed_by, status, created_at)
                SELECT :id, :itemId, minor_code, minor_code, label_version, '[]', :evidence,
                       'CS_AGENT', 'PROPOSED', :createdAt
                FROM pcb.symptom_definition WHERE minor_code = :minorCode
                """).param("id", id).param("itemId", itemId).param("minorCode", minorCode)
                .param("evidence", evidence).param("createdAt", createdAt).update();
    }

    public void saveClassification(String rmaId, String state, String classification, String questions, OffsetDateTime now) {
        jdbcClient.sql("""
                UPDATE app.intake SET intake_state = :state, classification_json = CAST(:classification AS jsonb),
                    follow_up_questions_json = CAST(:questions AS jsonb), updated_at = :now
                WHERE rma_id = :rmaId
                """).param("rmaId", rmaId).param("state", state).param("classification", classification)
                .param("questions", questions).param("now", now).update();
    }

    public void saveTriage(String rmaId, String json, boolean provisional, OffsetDateTime now) {
        jdbcClient.sql("""
                UPDATE app.intake SET intake_state = :state, triage_result_json = CAST(:result AS jsonb),
                    triage_proposal_json = CAST(:proposal AS jsonb), updated_at = :now
                WHERE rma_id = :rmaId
                """).param("rmaId", rmaId).param("state", provisional ? "TRIAGE_REVIEW" : "PROCESSING")
                .param("result", json).param("proposal", provisional ? json : null).param("now", now).update();
    }

    public void updateQaStatus(String rmaId, String status) {
        jdbcClient.sql("UPDATE pcb.rma_claim SET qa_status = :status WHERE rma_id = :rmaId")
                .param("rmaId", rmaId).param("status", status).update();
    }

    public void saveAnalysis(AnalysisRow row) {
        jdbcClient.sql("""
                INSERT INTO app.ai_analysis (analysis_id, rma_id, mode, analysis_state, created_at, completed_at,
                    error_json, updated_at, poll_interval_ms, next_poll_at)
                VALUES (:analysisId, :rmaId, :mode, :state, :createdAt, :completedAt, CAST(:error AS jsonb),
                    :createdAt, :pollIntervalMs, CURRENT_TIMESTAMP + (:pollIntervalMs * INTERVAL '1 millisecond'))
                """).paramSource(row).update();
    }

    public void finish(String rmaId, String state, String errorCode, String errorDetail, OffsetDateTime now) {
        jdbcClient.sql("""
                UPDATE app.intake SET intake_state = :state, error_code = :errorCode, error_detail = :errorDetail,
                    updated_at = :now WHERE rma_id = :rmaId
                """).param("rmaId", rmaId).param("state", state).param("errorCode", errorCode)
                .param("errorDetail", errorDetail).param("now", now).update();
    }

    public Snapshot findSnapshot(String rmaId) {
        return jdbcClient.sql("""
                SELECT c.rma_id, c.cs_status, c.qa_status, i.intake_state, i.classification_json::text AS classification,
                       i.triage_result_json::text AS triage, i.follow_up_questions_json::text AS questions,
                       i.error_code, i.error_detail, a.analysis_id, a.analysis_state
                FROM app.intake i JOIN pcb.rma_claim c ON c.rma_id = i.rma_id
                LEFT JOIN LATERAL (
                    SELECT analysis_id, analysis_state FROM app.ai_analysis WHERE rma_id = i.rma_id
                    ORDER BY created_at DESC, poll_started_at DESC, analysis_id DESC LIMIT 1
                ) a ON TRUE
                WHERE i.rma_id = :rmaId
                """).param("rmaId", rmaId).query(Snapshot.class).single();
    }

    public record Input(String rmaId, String rmaItemId, String rawText, String detectionStage,
                        String reportedPartNo, String customerId, long claimedDefectQty) {
    }

    public record AnalysisRow(String analysisId, String rmaId, String mode, String state,
                              OffsetDateTime createdAt, OffsetDateTime completedAt, String error, int pollIntervalMs) {
    }

    public record Snapshot(String rmaId, String csStatus, String qaStatus, String intakeState, String classification,
                           String triage, String questions, String errorCode, String errorDetail,
                           String analysisId, String analysisState) {
    }
}
