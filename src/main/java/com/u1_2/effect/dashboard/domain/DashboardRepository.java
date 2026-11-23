package com.u1_2.effect.dashboard.domain;

import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DashboardRepository {

    private final JdbcClient jdbcClient;

    public Kpis findKpis(OffsetDateTime periodStart, OffsetDateTime periodEnd) {
        // 품목이나 처리 실행이 여럿이어도 종료일은 마지막 실행 시각, 건수는 접수당 한 건임
        return jdbcClient.sql("""
                WITH closed_claims AS (
                    SELECT c.rma_id, max(a.executed_at::timestamptz) AS closed_at
                    FROM pcb.rma_claim c
                    JOIN pcb.rma_item i ON i.rma_id = c.rma_id
                    JOIN pcb.service_action a ON a.rma_item_id = i.rma_item_id
                    WHERE c.cs_status = 'CLOSED' AND a.executed_at IS NOT NULL
                    GROUP BY c.rma_id
                )
                SELECT count(*) FILTER (WHERE c.opened_at::timestamptz >= :periodStart
                                            AND c.opened_at::timestamptz < :periodEnd) AS received_count,
                       count(*) FILTER (WHERE c.cs_status <> 'CLOSED') AS pending_count,
                       (SELECT count(*) FROM closed_claims
                        WHERE closed_at >= :periodStart AND closed_at < :periodEnd) AS closed_count,
                       (SELECT coalesce(sum(amount_krw), 0) FROM pcb.loss_ledger
                        WHERE occurred_at::timestamptz >= :periodStart
                          AND occurred_at::timestamptz < :periodEnd) AS estimated_loss_krw
                FROM pcb.rma_claim c
                """).param("periodStart", periodStart).param("periodEnd", periodEnd)
                .query(Kpis.class).single();
    }

    public record Kpis(long receivedCount, long pendingCount, long closedCount, long estimatedLossKrw) {
    }
}
