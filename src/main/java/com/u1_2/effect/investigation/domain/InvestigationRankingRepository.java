package com.u1_2.effect.investigation.domain;

import com.u1_2.effect.global.time.KoreaDateRange;
import com.u1_2.effect.global.time.KoreaTime;
import com.u1_2.effect.rma.domain.RmaHistoryOrder;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class InvestigationRankingRepository {

    private final JdbcClient jdbcClient;

    public List<Investigation> find(KoreaDateRange period, String product) {
        // 연결 이력이 중복돼도 조사마다 접수 건수·품목 수량·손실을 한 번씩 집계함
        return InvestigationClaimSql.comparison(jdbcClient, period,
                InvestigationClaimSql.SCOPED_CLAIMS + InvestigationClaimSql.CLASSIFIED_CLAIMS + """
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
                SELECT i.investigation_id, i.title, i.analysis_level, i.status, c.reported_part_no AS product, c.defect_type,
                       a.cause_name, a.certainty_status = 'CONFIRMED' AS cause_confirmed,
                       n.count, n.previous_count, n.recent_count, n.evidence,
                       m.defect_qty, m.severity, m.recurrence, coalesce(l.total_krw, 0) AS loss
                FROM member_counts n
                JOIN pcb.investigation i USING (investigation_id)
                JOIN metrics m USING (investigation_id)
                JOIN classified_claims c ON c.rma_id = n.evidence[1]
                LEFT JOIN pcb.loss_by_investigation l USING (investigation_id)
                LEFT JOIN LATERAL (
                    SELECT cm.cause_name, ca.certainty_status FROM pcb.cause_assessment ca
                    JOIN pcb.cause_master cm USING (cause_code)
                    WHERE ca.investigation_id = i.investigation_id AND ca.approved_at::timestamptz < :end
                    ORDER BY ca.approved_at::timestamptz DESC, ca.cause_assessment_id DESC
                    LIMIT 1
                ) a ON true
                """.formatted(RmaHistoryOrder.TRIAGE), product).param("recentStart", KoreaTime.startOfDay(period.to().minusDays(29)))
                .query((row, index) -> new Investigation(
                        row.getString("investigation_id"), row.getString("title"), row.getString("analysis_level"),
                        row.getString("status"),
                        row.getString("product"), row.getString("defect_type"), row.getString("cause_name"),
                        row.getBoolean("cause_confirmed"),
                        row.getLong("count"), row.getLong("previous_count"), row.getLong("recent_count"),
                        List.of((String[]) row.getArray("evidence").getArray()),
                        row.getLong("defect_qty"), row.getInt("severity"), row.getBoolean("recurrence"),
                        row.getLong("loss")
                )).list();
    }

    public record Investigation(
            String investigationId, String title, String analysisLevel, String status, String product, String defectType,
            String causeName, boolean causeConfirmed, long count, long previousCount, long recentCount, List<String> evidence,
            long defectQty, int severity, boolean recurrence, long loss
    ) {
        public String rmaId() {
            return evidence.getFirst();
        }
    }
}
