package com.u1_2.effect.dashboard.domain;

import com.u1_2.effect.rma.domain.RmaHistoryOrder;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class QualityDashboardRepository {

    private static final String SCOPED_CLAIMS = """
            WITH claims AS (
                SELECT rma_id, reported_part_no, opened_at::timestamptz AS opened_at,
                       opened_at::timestamptz >= :start AS is_current
                FROM pcb.rma_claim
                WHERE opened_at::timestamptz >= :previousStart AND opened_at::timestamptz < :end
            ), current_claims AS (
                SELECT * FROM claims WHERE is_current
            )
            """ + DashboardClaimSql.CURRENT_ITEMS + """
            , reviews AS (
                SELECT DISTINCT ON (item.rma_item_id) item.rma_id, r.verdict
                FROM current_items item JOIN pcb.recurrence_review r USING (rma_item_id)
                WHERE r.reviewed_at::timestamptz < :end
                ORDER BY item.rma_item_id, r.reviewed_at::timestamptz DESC, r.review_id DESC
            )
            """;

    private final JdbcClient jdbcClient;

    public Totals findTotals(Window window) {
        // 접수와 원인 확정은 RMA 단위, 대책 효과는 완료 대책 단위로 각각 한 번만 셈
        return query(window, SCOPED_CLAIMS + """
                , receipt_counts AS (
                    SELECT count(*) FILTER (WHERE is_current) AS received,
                           count(*) FILTER (WHERE NOT is_current) AS previous_received
                    FROM claims
                ), loss_totals AS (
                    SELECT coalesce(sum(amount_krw) FILTER (WHERE occurred_at::timestamptz >= :start), 0) AS loss,
                           coalesce(sum(amount_krw) FILTER (WHERE occurred_at::timestamptz < :start), 0) AS previous_loss
                    FROM pcb.loss_ledger
                    WHERE occurred_at::timestamptz >= :previousStart AND occurred_at::timestamptz < :end
                ), confirmed AS (
                    SELECT count(DISTINCT item.rma_id) AS confirmed
                    FROM current_items item
                    JOIN pcb.claim_cause link USING (rma_item_id)
                    JOIN pcb.cause_assessment ca USING (cause_assessment_id)
                    WHERE link.applicability IN ('APPLIES', 'APPLIES_PHOTO_ONLY')
                      AND link.decided_at::timestamptz < :end
                      AND ca.certainty_status = 'CONFIRMED' AND ca.approved_at::timestamptz < :end
                ), actions AS (
                    SELECT count(*) AS completed_actions,
                           count(*) FILTER (WHERE check_result.result = 'EFFECTIVE'
                               AND NOT EXISTS (
                                   SELECT 1 FROM pcb.recurrence_review r
                                   WHERE r.corrective_action_id = a.corrective_action_id
                                     AND r.verdict = 'RECURRENCE' AND r.reviewed_at::timestamptz < :end
                               )) AS effective_actions
                    FROM pcb.corrective_action a
                    LEFT JOIN LATERAL (
                        SELECT e.result FROM pcb.effectiveness_check e
                        WHERE e.corrective_action_id = a.corrective_action_id AND e.checked_at::timestamptz < :end
                        ORDER BY e.checked_at::timestamptz DESC, e.check_id DESC
                        LIMIT 1
                    ) check_result ON true
                    WHERE a.implemented_at::timestamptz >= :start AND a.implemented_at::timestamptz < :end
                )
                SELECT receipt_counts.*, loss_totals.*, confirmed.*, actions.*,
                       (SELECT count(DISTINCT rma_id) FROM reviews
                        WHERE verdict = 'PENDING_CAUSE_REVIEW') AS recurrence_suspected
                FROM receipt_counts CROSS JOIN loss_totals CROSS JOIN confirmed CROSS JOIN actions
                """).query(Totals.class).single();
    }

    public List<DefectCount> findDefects(Window window) {
        // 목록의 defectType 필터와 같은 대분류를 쓰며 분류 전 접수는 집계에서 제외함
        return query(window, SCOPED_CLAIMS + DashboardClaimSql.CLASSIFIED_CLAIMS + """
                SELECT defect_type, count(*) AS count
                FROM classified_claims
                WHERE defect_type IS NOT NULL
                GROUP BY defect_type
                ORDER BY count DESC, defect_type
                LIMIT 5
                """).query(DefectCount.class).list();
    }

    public List<Investigation> findInvestigations(Window window, OffsetDateTime recentStart) {
        // 연결 이력이 중복돼도 조사마다 접수 건수·품목 수량·손실을 한 번씩 집계함
        return query(window, SCOPED_CLAIMS + DashboardClaimSql.CLASSIFIED_CLAIMS + """
                , members AS (
                    SELECT DISTINCT ic.investigation_id, c.rma_id, c.opened_at, c.is_current
                    FROM pcb.investigation_claim ic
                    JOIN claims c USING (rma_id)
                    WHERE ic.linked_at::timestamptz < :end
                ), member_counts AS (
                    SELECT investigation_id,
                           count(*) FILTER (WHERE is_current) AS count,
                           count(*) FILTER (WHERE NOT is_current) AS previous_count,
                           count(*) FILTER (WHERE is_current AND opened_at >= :recentStart) AS recent_count,
                           (array_agg(rma_id ORDER BY opened_at DESC, rma_id) FILTER (WHERE is_current))[1 : 5]
                               AS evidence
                    FROM members GROUP BY investigation_id
                    HAVING bool_or(is_current)
                ), item_totals AS (
                    SELECT rma_id, sum(verified_defect_qty) AS defect_qty
                    FROM current_items GROUP BY rma_id
                ), latest_triage AS (
                    SELECT DISTINCT ON (triage.rma_id) triage.rma_id, triage.severity
                    FROM pcb.qa_triage triage JOIN current_claims c USING (rma_id)
                    WHERE triage.decided_at::timestamptz < :end
                    ORDER BY triage.rma_id, %s
                ), recurrence_claims AS (
                    SELECT DISTINCT rma_id FROM reviews
                    WHERE verdict IN ('RECURRENCE', 'PENDING_CAUSE_REVIEW')
                ), metrics AS (
                    SELECT m.investigation_id, coalesce(sum(q.defect_qty), 0) AS defect_qty,
                           coalesce(max(t.severity), 0) AS severity, bool_or(r.rma_id IS NOT NULL) AS recurrence
                    FROM members m
                    LEFT JOIN item_totals q USING (rma_id)
                    LEFT JOIN latest_triage t USING (rma_id)
                    LEFT JOIN recurrence_claims r USING (rma_id)
                    WHERE m.is_current GROUP BY m.investigation_id
                )
                SELECT i.investigation_id, i.title, i.analysis_level, c.reported_part_no AS product, c.defect_type,
                       a.cause_name, n.count, n.previous_count, n.recent_count, n.evidence,
                       m.defect_qty, m.severity, m.recurrence, coalesce(l.total_krw, 0) AS loss
                FROM member_counts n
                JOIN pcb.investigation i USING (investigation_id)
                JOIN metrics m USING (investigation_id)
                JOIN classified_claims c ON c.rma_id = n.evidence[1]
                LEFT JOIN pcb.loss_by_investigation l USING (investigation_id)
                LEFT JOIN LATERAL (
                    SELECT cm.cause_name FROM pcb.cause_assessment ca
                    JOIN pcb.cause_master cm USING (cause_code)
                    WHERE ca.investigation_id = i.investigation_id AND ca.approved_at::timestamptz < :end
                    ORDER BY ca.approved_at::timestamptz DESC, ca.cause_assessment_id DESC
                    LIMIT 1
                ) a ON true
                """.formatted(RmaHistoryOrder.TRIAGE)).param("recentStart", recentStart)
                .query((row, index) -> new Investigation(
                        row.getString("investigation_id"), row.getString("title"), row.getString("analysis_level"),
                        row.getString("product"), row.getString("defect_type"), row.getString("cause_name"),
                        row.getLong("count"), row.getLong("previous_count"), row.getLong("recent_count"),
                        List.of((String[]) row.getArray("evidence").getArray()),
                        row.getLong("defect_qty"), row.getInt("severity"), row.getBoolean("recurrence"),
                        row.getLong("loss")
                )).list();
    }

    private JdbcClient.StatementSpec query(Window window, String sql) {
        return jdbcClient.sql(sql).param("previousStart", window.previousStart())
                .param("start", window.start()).param("end", window.end());
    }

    public record Window(OffsetDateTime previousStart, OffsetDateTime start, OffsetDateTime end) {
    }

    public record Totals(
            long received, long previousReceived, long loss, long previousLoss, long confirmed,
            long completedActions, long effectiveActions, long recurrenceSuspected
    ) {
    }

    public record DefectCount(String defectType, long count) {
    }

    public record Investigation(
            String investigationId, String title, String analysisLevel, String product, String defectType,
            String causeName, long count, long previousCount, long recentCount, List<String> evidence,
            long defectQty, int severity, boolean recurrence, long loss
    ) {
        public String rmaId() {
            return evidence.getFirst();
        }
    }
}
