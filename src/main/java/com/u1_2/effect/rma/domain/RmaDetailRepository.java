package com.u1_2.effect.rma.domain;

import com.u1_2.effect.global.time.KoreaTime;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * 원본 pcb 데이터와 Spring이 관리하는 app 상태를 결합해 상세 조회용으로 읽음.
 */
@Repository
@RequiredArgsConstructor
public class RmaDetailRepository {

    private final JdbcClient jdbcClient;
    private final JsonMapper jsonMapper;

    public Optional<Claim> findClaim(String rmaId, boolean includeTriageProposal) {
        // 기존 v5 접수도 조회되도록 신규 접수 상태나 분석 기록이 없어도 원본 행을 유지함
        return jdbcClient.sql("""
                SELECT c.rma_id, c.opened_at, c.occurred_at, c.raw_text, c.reported_part_no,
                       c.reported_lot_no, c.reported_date_code, c.detection_stage, c.cs_status, c.qa_status,
                       u.customer_id, u.customer_name, u.industry,
                       COALESCE(i.intake_state, 'ACCEPTED') AS intake_state,
                       COALESCE(a.analysis_state, 'NOT_REQUESTED') AS analysis_state,
                       COALESCE(d.approval_status, 'NOT_REQUIRED') AS approval_status,
                       CASE WHEN :includeTriageProposal AND i.intake_state = 'TRIAGE_REVIEW'
                            THEN NULLIF(i.triage_proposal_json, 'null'::jsonb)::text END AS triage_proposal
                FROM pcb.rma_claim c
                JOIN pcb.customer u ON u.customer_id = c.customer_id
                LEFT JOIN app.intake i ON i.rma_id = c.rma_id
                LEFT JOIN LATERAL (
                    SELECT analysis_id, analysis_state
                    FROM app.ai_analysis analysis
                    WHERE rma_id = c.rma_id
                    ORDER BY %s
                    LIMIT 1
                ) a ON TRUE
                LEFT JOIN app.cause_decision d ON d.analysis_id = a.analysis_id
                WHERE c.rma_id = :rmaId
                """.formatted(RmaHistoryOrder.ANALYSIS)).param("rmaId", rmaId)
                .param("includeTriageProposal", includeTriageProposal)
                .query((row, index) -> new Claim(
                        row.getString("rma_id"), readTime(row, "opened_at"), readTime(row, "occurred_at"),
                        row.getString("raw_text"), row.getString("reported_part_no"), row.getString("reported_lot_no"),
                        row.getString("reported_date_code"), row.getString("detection_stage"),
                        row.getString("cs_status"), row.getString("qa_status"), row.getString("customer_id"),
                        row.getString("customer_name"), row.getString("industry"), row.getString("intake_state"),
                        row.getString("analysis_state"), row.getString("approval_status"),
                        row.getString("triage_proposal")))
                .optional();
    }

    public List<Item> findItems(String rmaId) {
        return jdbcClient.sql("""
                SELECT rma_item_id, panel_id, input_qty, claimed_defect_qty,
                       returned_qty, verified_defect_qty, lot_id
                FROM pcb.rma_item
                WHERE rma_id = :rmaId
                ORDER BY rma_item_id
                """).param("rmaId", rmaId).query(Item.class).list();
    }

    public Optional<Symptom> findSymptom(String rmaId) {
        // 품목의 최신 분류 결과에 분류표의 표시 이름을 붙여 조회함
        return jdbcClient.sql("""
                SELECT symptom.primary_label, d.minor_name, d.major_name, symptom.status, symptom.evidence_spans_json
                FROM pcb.symptom_assessment symptom
                JOIN pcb.rma_item i ON i.rma_item_id = symptom.rma_item_id
                JOIN pcb.symptom_definition d ON d.minor_code = symptom.primary_label
                WHERE i.rma_id = :rmaId
                ORDER BY %s
                LIMIT 1
                """.formatted(RmaHistoryOrder.SYMPTOM)).param("rmaId", rmaId)
                .query((row, index) -> new Symptom(
                        row.getString("primary_label"), row.getString("minor_name"), row.getString("major_name"),
                        row.getString("status"),
                        List.of(jsonMapper.readValue(row.getString("evidence_spans_json"), String[].class))))
                .optional();
    }

    public Optional<Triage> findTriage(String rmaId) {
        return jdbcClient.sql("""
                SELECT severity, characteristic_symbol, analysis_level, rationale, decided_by, decided_at
                FROM pcb.qa_triage triage
                WHERE rma_id = :rmaId
                ORDER BY %s
                LIMIT 1
                """.formatted(RmaHistoryOrder.TRIAGE)).param("rmaId", rmaId)
                .query((row, index) -> new Triage(row.getInt("severity"), row.getString("characteristic_symbol"),
                        row.getString("analysis_level"), row.getString("rationale"), row.getString("decided_by"),
                        readTime(row, "decided_at")))
                .optional();
    }

    private static OffsetDateTime readTime(ResultSet row, String column) throws SQLException {
        return KoreaTime.inSeoul(OffsetDateTime.parse(row.getString(column)));
    }

    public record Claim(
            String rmaId, OffsetDateTime openedAt, OffsetDateTime occurredAt, String rawText,
            String reportedPartNo, String reportedLotNo, String reportedDateCode, String detectionStage,
            String csStatus, String qaStatus, String customerId, String customerName, String industry,
            String intakeState, String analysisState, String approvalStatus, String triageProposal
    ) {
    }

    public record Item(
            String rmaItemId, String panelId, long inputQty, long claimedDefectQty, long returnedQty,
            Long verifiedDefectQty, String lotId
    ) {
    }

    public record Symptom(
            String primaryLabel, String minorName, String majorName, String status, List<String> evidenceSpans
    ) {
    }

    public record Triage(
            int severity, String characteristicSymbol, String analysisLevel, String rationale,
            String decidedBy, OffsetDateTime decidedAt
    ) {
    }
}
