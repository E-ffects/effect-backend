package com.u1_2.effect.rma.domain.review;

import com.u1_2.effect.rma.domain.RmaHistoryOrder;
import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 현재 QA 판정은 pcb에, 변경 전후 이력과 판정 대기 등급은 app에 저장함.
 */
@Repository
@RequiredArgsConstructor
public class RmaTriageRepository {

    private final JdbcClient jdbcClient;

    public Context findContext(String rmaId) {
        // 최초 자동 등급과 최신 원인 판정 상태를 함께 읽어 수정 가능 여부를 판단함
        return jdbcClient.sql("""
                SELECT c.qa_status, i.intake_state, i.triage_proposal_json::text AS proposal,
                       COALESCE(i.triage_result_json ->> 'analysis_level', h.original_level) AS original_level,
                       EXISTS (SELECT 1 FROM app.cause_decision d WHERE d.rma_id = c.rma_id
                               AND d.approval_status IN ('APPROVED', 'CONFIRMED')) OR
                           c.qa_status = 'CAUSE_DECIDED' OR COALESCE(latest.approval_status = 'REJECTED', FALSE) AS locked
                FROM pcb.rma_claim c
                LEFT JOIN app.intake i ON i.rma_id = c.rma_id
                LEFT JOIN LATERAL (
                    SELECT before_json ->> 'analysis_level' AS original_level FROM app.triage_history
                    WHERE rma_id = c.rma_id ORDER BY id LIMIT 1
                ) h ON TRUE
                LEFT JOIN LATERAL (
                    SELECT d.approval_status FROM app.ai_analysis analysis
                    LEFT JOIN app.cause_decision d ON d.analysis_id = analysis.analysis_id
                    WHERE analysis.rma_id = c.rma_id
                    ORDER BY %s LIMIT 1
                ) latest ON TRUE
                WHERE c.rma_id = :rmaId
                """.formatted(RmaHistoryOrder.ANALYSIS)).param("rmaId", rmaId).query(Context.class).single();
    }

    public Optional<Decision> findLatest(String rmaId) {
        return jdbcClient.sql("""
                SELECT triage_id, rma_id, severity, characteristic_id, characteristic_symbol, customer_industry,
                       repeat_signal, analysis_level, rationale, decided_by, decided_at
                FROM pcb.qa_triage triage WHERE rma_id = :rmaId
                ORDER BY %s LIMIT 1
                """.formatted(RmaHistoryOrder.TRIAGE)).param("rmaId", rmaId).query(Decision.class).optional();
    }

    public Optional<Decision> findManual(String rmaId) {
        return findLatest(rmaId).filter(decision -> !"QA_AGENT".equals(decision.decidedBy()));
    }

    public void save(Decision decision) {
        // 기존 QA 행은 같은 번호로 갱신하고 바뀌기 전 값은 이력에 보관함
        jdbcClient.sql("""
                INSERT INTO pcb.qa_triage (triage_id, rma_id, severity, characteristic_id, characteristic_symbol,
                    customer_industry, repeat_signal, analysis_level, rationale, decided_by, decided_at)
                VALUES (:triageId, :rmaId, :severity, :characteristicId, :characteristicSymbol, :customerIndustry,
                    :repeatSignal, :analysisLevel, :rationale, :decidedBy, :decidedAt)
                ON CONFLICT (triage_id) DO UPDATE SET severity = EXCLUDED.severity,
                    characteristic_id = EXCLUDED.characteristic_id, characteristic_symbol = EXCLUDED.characteristic_symbol,
                    analysis_level = EXCLUDED.analysis_level, rationale = EXCLUDED.rationale,
                    decided_by = EXCLUDED.decided_by, decided_at = EXCLUDED.decided_at
                """).paramSource(decision).update();
    }

    public void saveHistory(String rmaId, String before, String after, String editor, OffsetDateTime now) {
        jdbcClient.sql("""
                INSERT INTO app.triage_history (rma_id, before_json, after_json, changed_by, changed_at)
                VALUES (:rmaId, CAST(:before AS jsonb), CAST(:after AS jsonb), :editor, :now)
                """).param("rmaId", rmaId).param("before", before).param("after", after)
                .param("editor", editor).param("now", now).update();
    }

    public void markConfirmed(String rmaId, OffsetDateTime now) {
        jdbcClient.sql("""
                UPDATE app.intake SET intake_state = 'PROCESSING', triage_proposal_json = NULL, updated_at = :now
                WHERE rma_id = :rmaId AND intake_state = 'TRIAGE_REVIEW'
                """).param("rmaId", rmaId).param("now", now).update();
    }

    public void updateWaitingLevel(String rmaId, String level) {
        jdbcClient.sql("""
                UPDATE app.cause_decision SET analysis_level = :level
                WHERE rma_id = :rmaId AND approval_status = 'WAITING'
                """).param("rmaId", rmaId).param("level", level).update();
    }

    public record Context(String qaStatus, String intakeState, String proposal, String originalLevel, boolean locked) {
    }

    public record Decision(String triageId, String rmaId, int severity, String characteristicId,
                           String characteristicSymbol, String customerIndustry, int repeatSignal, String analysisLevel,
                           String rationale, String decidedBy, String decidedAt) {
    }
}
