package com.u1_2.effect.dashboard.domain;

import com.u1_2.effect.rma.domain.RmaHistoryOrder;

final class DashboardClaimSql {

    static final String CURRENT_ITEMS = """
            , current_items AS (
                SELECT item.rma_item_id, item.rma_id, item.verified_defect_qty
                FROM current_claims c JOIN pcb.rma_item item USING (rma_id)
            )
            """;

    // 추이와 품질 대시보드의 유형 집계가 같은 최신 판정과 접수 분류를 읽음
    static final String CLASSIFIED_CLAIMS = """
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

    private DashboardClaimSql() {
    }
}
