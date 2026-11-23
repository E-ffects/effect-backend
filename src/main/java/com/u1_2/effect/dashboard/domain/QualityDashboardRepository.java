package com.u1_2.effect.dashboard.domain;

import com.u1_2.effect.global.time.KoreaDateRange;
import com.u1_2.effect.investigation.domain.InvestigationClaimSql;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class QualityDashboardRepository {

    private final JdbcClient jdbcClient;

    public Totals findTotals(KoreaDateRange period) {
        // 접수와 원인 확정은 RMA 단위, 대책 효과는 완료 대책 단위로 각각 한 번만 셈
        return InvestigationClaimSql.comparison(jdbcClient, period, InvestigationClaimSql.SCOPED_CLAIMS + """
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
                       (SELECT count(DISTINCT r.rma_id) FROM reviews r
                        WHERE r.verdict = 'PENDING_CAUSE_REVIEW' AND NOT EXISTS (
                            SELECT 1 FROM app.rma_recurrence_decision d
                            WHERE d.rma_id = r.rma_id AND d.decided_at < :end
                        )) AS recurrence_suspected
                FROM receipt_counts CROSS JOIN loss_totals CROSS JOIN confirmed CROSS JOIN actions
                """, null).query(Totals.class).single();
    }

    public List<DefectCount> findDefects(KoreaDateRange period) {
        // 목록의 defectType 필터와 같은 대분류를 쓰며 분류 전 접수는 집계에서 제외함
        return InvestigationClaimSql.comparison(jdbcClient, period, InvestigationClaimSql.SCOPED_CLAIMS + InvestigationClaimSql.CLASSIFIED_CLAIMS + """
                SELECT defect_type, count(*) AS count
                FROM classified_claims
                WHERE defect_type IS NOT NULL
                GROUP BY defect_type
                ORDER BY count DESC, defect_type
                LIMIT 5
                """, null).query(DefectCount.class).list();
    }

    public record Totals(
            long received, long previousReceived, long loss, long previousLoss, long confirmed,
            long completedActions, long effectiveActions, long recurrenceSuspected
    ) {
    }

    public record DefectCount(String defectType, long count) {
    }
}
