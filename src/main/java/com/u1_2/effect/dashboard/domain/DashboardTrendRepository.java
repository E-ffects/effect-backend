package com.u1_2.effect.dashboard.domain;

import com.u1_2.effect.investigation.domain.InvestigationClaimSql;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DashboardTrendRepository {

    // 제품과 불량이 함께 오면 반드시 같은 접수에서 두 조건이 맞아야 함
    private static final String CLAIM_FILTER = """
            (CAST(:product AS text) IS NULL OR reported_part_no = :product)
            AND (CAST(:defectType AS text) IS NULL OR defect_type = :defectType)
            """;

    private final JdbcClient jdbcClient;

    public List<DailyCount> findDailyCounts(Filter filter) {
        return query(filter, """
                WITH current_claims AS (
                    SELECT rma_id, reported_part_no, opened_at::timestamptz AS opened_at
                    FROM pcb.rma_claim
                    WHERE opened_at::timestamptz >= :start AND opened_at::timestamptz < :end
                )
                """ + InvestigationClaimSql.CURRENT_ITEMS + InvestigationClaimSql.CLASSIFIED_CLAIMS + """
                SELECT (opened_at AT TIME ZONE 'Asia/Seoul')::date AS day, count(*) AS count
                FROM classified_claims
                WHERE
                """ + CLAIM_FILTER + """
                GROUP BY day ORDER BY day
                """).query(DailyCount.class).list();
    }

    public List<ActionMarker> findActionMarkers(Filter filter) {
        // 대책은 접수일과 별개인 시행일에 찍으며, 조사에 예전 접수가 연결된 대책도 포함함
        return query(filter, """
                WITH actions AS (
                    SELECT corrective_action_id, investigation_id, action_category,
                           (implemented_at::timestamptz AT TIME ZONE 'Asia/Seoul')::date AS day
                    FROM pcb.corrective_action
                    WHERE implemented_at::timestamptz >= :start AND implemented_at::timestamptz < :end
                ), links AS (
                    SELECT DISTINCT a.corrective_action_id, ic.rma_id
                    FROM actions a JOIN pcb.investigation_claim ic USING (investigation_id)
                    WHERE (CAST(:product AS text) IS NOT NULL OR CAST(:defectType AS text) IS NOT NULL)
                      AND ic.linked_at::timestamptz < :end
                ), current_claims AS (
                    SELECT c.rma_id, c.reported_part_no
                    FROM pcb.rma_claim c
                    WHERE c.opened_at::timestamptz < :end
                      AND EXISTS (SELECT 1 FROM links l WHERE l.rma_id = c.rma_id)
                )
                """ + InvestigationClaimSql.CURRENT_ITEMS + InvestigationClaimSql.CLASSIFIED_CLAIMS + """
                SELECT a.day, concat_ws(' ', a.corrective_action_id, a.action_category) AS label
                FROM actions a
                WHERE (CAST(:product AS text) IS NULL AND CAST(:defectType AS text) IS NULL)
                   OR EXISTS (
                       SELECT 1 FROM links l JOIN classified_claims c USING (rma_id)
                       WHERE l.corrective_action_id = a.corrective_action_id
                         AND
                """ + CLAIM_FILTER + """
                   )
                ORDER BY a.day, a.corrective_action_id
                """).query(ActionMarker.class).list();
    }

    private JdbcClient.StatementSpec query(Filter filter, String sql) {
        return jdbcClient.sql(sql).param("start", filter.start()).param("end", filter.end())
                .param("product", filter.product()).param("defectType", filter.defectType());
    }

    public record Filter(OffsetDateTime start, OffsetDateTime end, String product, String defectType) {
    }

    public record DailyCount(LocalDate day, long count) {
    }

    public record ActionMarker(LocalDate day, String label) {
    }
}
