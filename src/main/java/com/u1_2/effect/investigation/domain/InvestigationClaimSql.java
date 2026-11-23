package com.u1_2.effect.investigation.domain;

import com.u1_2.effect.global.time.KoreaDateRange;
import com.u1_2.effect.rma.domain.RmaHistoryOrder;
import org.springframework.jdbc.core.simple.JdbcClient;

public final class InvestigationClaimSql {

    public static final String CURRENT_ITEMS = """
            , current_items AS (
                SELECT item.rma_item_id, item.rma_id, item.verified_defect_qty
                FROM current_claims c JOIN pcb.rma_item item USING (rma_id)
            )
            """;

    // 추이와 품질 대시보드의 유형 집계가 같은 최신 판정과 접수 분류를 읽음
    public static final String CLASSIFIED_CLAIMS = """
            , latest_symptoms AS (
                SELECT DISTINCT ON (item.rma_id) item.rma_id, d.major_name
                FROM current_items item
                JOIN pcb.symptom_assessment symptom USING (rma_item_id)
                JOIN pcb.symptom_definition d ON d.minor_code = symptom.primary_label
                WHERE symptom.created_at::timestamptz < :end
                ORDER BY item.rma_id, %s
            ), classified_claims AS (
                SELECT c.*, coalesce(s.major_name, i.classification_json ->> 'major_name') AS defect_type
                FROM current_claims c
                LEFT JOIN app.intake i USING (rma_id)
                LEFT JOIN latest_symptoms s USING (rma_id)
            )
            """.formatted(RmaHistoryOrder.SYMPTOM);

    public static final String SCOPED_CLAIMS = """
            WITH claims AS (
                SELECT rma_id, reported_part_no, opened_at::timestamptz AS opened_at,
                       opened_at::timestamptz >= :start AS is_current
                FROM pcb.rma_claim
                WHERE opened_at::timestamptz >= :previousStart AND opened_at::timestamptz < :end
                  AND (CAST(:product AS text) IS NULL OR reported_part_no = :product)
            ), current_claims AS (
                SELECT * FROM claims WHERE is_current
            )
            """ + CURRENT_ITEMS + """
            , reviews AS (
                SELECT DISTINCT ON (item.rma_item_id) item.rma_id, r.verdict
                FROM current_items item JOIN pcb.recurrence_review r USING (rma_item_id)
                WHERE r.reviewed_at::timestamptz < :end
                ORDER BY item.rma_item_id, r.reviewed_at::timestamptz DESC, r.review_id DESC
            )
            """;

    private InvestigationClaimSql() {
    }

    // 집계와 순위가 같은 날짜 경계 및 직전 동기간으로 조회하게 함
    public static JdbcClient.StatementSpec comparison(
            JdbcClient client, KoreaDateRange period, String sql, String product
    ) {
        return client.sql(sql).param("previousStart", period.previousStart()).param("start", period.start())
                .param("end", period.end()).param("product", product);
    }
}
