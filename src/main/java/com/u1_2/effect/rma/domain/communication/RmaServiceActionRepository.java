package com.u1_2.effect.rma.domain.communication;

import com.u1_2.effect.global.dto.PageParameters;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RmaServiceActionRepository {

    private static final String FROM_CLAIM = """
            FROM pcb.service_action a
            JOIN pcb.rma_item i USING (rma_item_id)
            WHERE i.rma_id = :rmaId
            """;

    private final JdbcClient jdbcClient;

    public List<ActionRow> findHistory(String rmaId, PageParameters paging) {
        // 모든 품목의 기록을 합치고 실행된 기록은 실행 시각, 예약은 결정 시각을 기준으로 정렬함
        return jdbcClient.sql("""
                SELECT a.action_id, a.rma_item_id, a.action_type, a.policy_version, a.board_qty, a.result,
                       a.decided_at::timestamptz AS decided_at, a.executed_at::timestamptz AS executed_at
                """ + FROM_CLAIM + """
                ORDER BY COALESCE(a.executed_at::timestamptz, a.decided_at::timestamptz) DESC, a.action_id DESC
                LIMIT :size OFFSET :offset
                """).param("rmaId", rmaId).param("size", paging.size()).param("offset", paging.offset())
                .query(ActionRow.class).list();
    }

    public long countHistory(String rmaId) {
        return jdbcClient.sql("SELECT count(*) " + FROM_CLAIM).param("rmaId", rmaId).query(Long.class).single();
    }

    public Optional<String> findItemId(String rmaId) {
        return jdbcClient.sql("""
                SELECT rma_item_id FROM pcb.rma_item WHERE rma_id = :rmaId ORDER BY rma_item_id LIMIT 1
                """).param("rmaId", rmaId).query(String.class).optional();
    }

    public void insertServiceAction(ServiceAction action) {
        jdbcClient.sql("""
                INSERT INTO pcb.service_action (action_id, rma_item_id, action_type, policy_version,
                    decided_at, executed_at, board_qty, result)
                VALUES (:actionId, :itemId, :actionType, 'B2B-QA-2025', :decidedAt, :executedAt, :boardQty, :result)
                """).paramSource(action).update();
    }

    public record ActionRow(
            String actionId, String rmaItemId, String actionType, String policyVersion, long boardQty,
            String result, OffsetDateTime decidedAt, OffsetDateTime executedAt
    ) {
    }

    public record ServiceAction(String actionId, String itemId, String actionType, long boardQty,
                                String result, String decidedAt, String executedAt) {
    }
}
