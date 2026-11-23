package com.u1_2.effect.recurrence.domain;

import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RecurrenceDecisionRepository {

    private final JdbcClient jdbcClient;

    public void insert(String rmaId, RecurrenceDecision decision, long userId, String name, OffsetDateTime now) {
        // 한 RMA에 다른 판정이 동시에 와도 최초 확정만 저장함
        jdbcClient.sql("""
                INSERT INTO app.rma_recurrence_decision (rma_id, decision, decided_by, decided_by_name, decided_at)
                VALUES (:rmaId, :decision, :userId, :name, :now)
                ON CONFLICT (rma_id) DO NOTHING
                """).param("rmaId", rmaId).param("decision", decision.name())
                .param("userId", userId).param("name", name).param("now", now).update();
    }

    public Optional<Decision> find(String rmaId) {
        return jdbcClient.sql("""
                SELECT decision, decided_by_name, decided_at FROM app.rma_recurrence_decision WHERE rma_id = :rmaId
                """).param("rmaId", rmaId).query((row, index) -> new Decision(
                        RecurrenceDecision.valueOf(row.getString("decision")), row.getString("decided_by_name"),
                        row.getObject("decided_at", OffsetDateTime.class))).optional();
    }

    public record Decision(RecurrenceDecision decision, String decidedBy, OffsetDateTime decidedAt) {
    }
}
