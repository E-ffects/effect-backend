package com.u1_2.effect.rma.domain.review;

import com.u1_2.effect.rma.domain.RmaHistoryOrder;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 임시 판정 목록과 원인 판정 목록의 대상·페이지 조건을 DB에서 적용함.
 */
@Repository
@RequiredArgsConstructor
public class RmaReviewRepository {

    // 재분석 결과가 완료되면 이전 실행의 대기 행은 목록에서 숨기고 판정 완료 이력은 유지함
    private static final String APPROVALS = """
            FROM app.cause_decision d
            JOIN app.ai_analysis a ON a.analysis_id = d.analysis_id
            LEFT JOIN pcb.rma_claim c ON c.rma_id = d.rma_id
            LEFT JOIN pcb.customer u ON u.customer_id = c.customer_id
            LEFT JOIN app.intake i ON i.rma_id = d.rma_id
            LEFT JOIN LATERAL (
                SELECT analysis_level, severity, rationale, decided_by
                FROM pcb.qa_triage triage WHERE rma_id = d.rma_id
                ORDER BY %s LIMIT 1
            ) t ON TRUE
            WHERE d.approval_status = :status AND COALESCE(t.analysis_level, d.analysis_level) IN (:levels)
                AND (d.approval_status <> 'WAITING' OR a.analysis_id = (
                    SELECT analysis.analysis_id FROM app.ai_analysis analysis
                    WHERE analysis.rma_id = d.rma_id AND analysis.analysis_state IN ('SUCCEEDED', 'PARTIAL')
                    ORDER BY %s LIMIT 1
                ))
            """.formatted(RmaHistoryOrder.TRIAGE, RmaHistoryOrder.ANALYSIS);

    private final JdbcClient jdbcClient;

    public List<ApprovalRow> findApprovals(ApprovalStatus status, List<String> levels, long offset, int size) {
        // 등급 수정이 바로 목록에 반영되도록 현재 QA 판정을 먼저 사용함
        return approvals("""
                SELECT d.rma_id, d.analysis_id, u.customer_name, c.reported_part_no,
                    COALESCE(t.analysis_level, d.analysis_level) AS analysis_level,
                    COALESCE(NULLIF(a.result_json #> '{qa_review,approval_reasons}', 'null'::jsonb),
                             i.triage_result_json -> 'approval_reasons')::text AS approval_reasons,
                    a.result_json #>> '{cause_candidates,0,cause_code}' AS top_cause_code,
                    a.result_json #>> '{cause_candidates,0,cause_name}' AS top_cause_name,
                    COALESCE(a.completed_at, a.created_at) AS requested_at, d.approval_status,
                    t.severity, t.rationale, t.decided_by
                """, status, levels, "ORDER BY requested_at DESC, d.analysis_id DESC LIMIT :size OFFSET :offset")
                .param("size", size).param("offset", offset).query(ApprovalRow.class).list();
    }

    public long countApprovals(ApprovalStatus status, List<String> levels) {
        return approvals("SELECT COUNT(*)\n", status, levels, "").query(Long.class).single();
    }

    private JdbcClient.StatementSpec approvals(String select, ApprovalStatus status, List<String> levels, String suffix) {
        return jdbcClient.sql(select + APPROVALS + suffix).param("status", status.name()).param("levels", levels);
    }

    public List<TriageRow> findTriageReviews(long offset, int size) {
        return jdbcClient.sql("""
                SELECT c.rma_id, CAST(c.opened_at AS timestamptz) AS opened_at, u.customer_name, c.reported_part_no,
                       COALESCE(i.classification_json ->> 'minor_name', latest_symptom.minor_name) AS minor_name,
                       i.triage_proposal_json::text AS proposal
                FROM app.intake i
                JOIN pcb.rma_claim c ON c.rma_id = i.rma_id
                JOIN pcb.customer u ON u.customer_id = c.customer_id
                LEFT JOIN LATERAL (
                    SELECT d.minor_name
                    FROM pcb.rma_item ri JOIN pcb.symptom_assessment symptom ON symptom.rma_item_id = ri.rma_item_id
                    JOIN pcb.symptom_definition d ON d.minor_code = symptom.primary_label
                    WHERE ri.rma_id = i.rma_id
                    ORDER BY %s LIMIT 1
                ) latest_symptom ON TRUE
                WHERE i.intake_state = 'TRIAGE_REVIEW'
                ORDER BY opened_at DESC, c.rma_id DESC LIMIT :size OFFSET :offset
                """.formatted(RmaHistoryOrder.SYMPTOM)).param("size", size).param("offset", offset).query(TriageRow.class).list();
    }

    public long countTriageReviews() {
        return jdbcClient.sql("""
                SELECT COUNT(*) FROM app.intake i JOIN pcb.rma_claim c ON c.rma_id = i.rma_id
                JOIN pcb.customer u ON u.customer_id = c.customer_id WHERE i.intake_state = 'TRIAGE_REVIEW'
                """).query(Long.class).single();
    }

    public enum ApprovalStatus { WAITING, APPROVED, REJECTED, CONFIRMED }

    public enum ApprovalLevel {
        L3(List.of("L3")),
        L1_L2(List.of("L1", "L2"));

        private final List<String> levels;

        ApprovalLevel(List<String> levels) {
            this.levels = levels;
        }

        public List<String> levels() {
            return levels;
        }
    }

    public record ApprovalRow(String rmaId, String analysisId, String customerName, String reportedPartNo,
                              String analysisLevel, String approvalReasons,
                              String topCauseCode, String topCauseName, OffsetDateTime requestedAt, String approvalStatus,
                              Integer severity, String rationale, String decidedBy) {
    }

    public record TriageRow(String rmaId, OffsetDateTime openedAt, String customerName, String reportedPartNo,
                            String minorName, String proposal) {
    }
}
