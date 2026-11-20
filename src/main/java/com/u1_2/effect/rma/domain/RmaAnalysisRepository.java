package com.u1_2.effect.rma.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 분석 읽기와 작업 예약을 맡음. 원본 RMA와 별개인 app 스키마에 분석 실행별로 저장함.
 */
@Repository
@RequiredArgsConstructor
public class RmaAnalysisRepository {

    private final JdbcClient jdbcClient;

    public boolean lockRma(String rmaId) {
        // 재분석 등록과 QA 등급 수정을 접수 단위로 한 번에 하나씩 처리함
        return jdbcClient.sql("SELECT rma_id FROM pcb.rma_claim WHERE rma_id = :rmaId FOR UPDATE")
                .param("rmaId", rmaId).query(String.class).optional().isPresent();
    }

    public Optional<ReanalysisInput> findReanalysisInput(String rmaId) {
        return jdbcClient.sql("""
                SELECT c.raw_text, i.intake_state,
                       (SELECT COALESCE(SUM(ri.claimed_defect_qty), 0) FROM pcb.rma_item ri
                        WHERE ri.rma_id = c.rma_id) AS claimed_defect_qty,
                       EXISTS (SELECT 1 FROM pcb.qa_triage t WHERE t.rma_id = c.rma_id) AS triaged,
                       EXISTS (SELECT 1 FROM app.cause_decision d WHERE d.rma_id = c.rma_id
                               AND d.approval_status IN ('APPROVED', 'CONFIRMED')) AS decision_finalized
                FROM pcb.rma_claim c LEFT JOIN app.intake i ON i.rma_id = c.rma_id
                WHERE c.rma_id = :rmaId
                """).param("rmaId", rmaId).query(ReanalysisInput.class).optional();
    }

    public Optional<ActiveAnalysis> findActiveAnalysis(String rmaId) {
        return jdbcClient.sql("""
                SELECT analysis_id, analysis_state FROM app.ai_analysis
                WHERE rma_id = :rmaId AND analysis_state IN ('QUEUED', 'RUNNING')
                ORDER BY created_at DESC, poll_started_at DESC, analysis_id DESC LIMIT 1
                """).param("rmaId", rmaId).query(ActiveAnalysis.class).optional();
    }

    public Optional<Snapshot> findLatest(String rmaId) {
        return jdbcClient.sql("""
                SELECT c.rma_id, a.analysis_id, a.analysis_state, a.as_of, a.mode,
                       a.progress_json::text AS progress, a.result_json::text AS result,
                       a.trace_json::text AS trace, a.warnings_json::text AS warnings,
                       a.error_json::text AS error, a.langsmith_run_id,
                       COALESCE(a.updated_at, a.completed_at, a.created_at) AS updated_at
                FROM pcb.rma_claim c
                LEFT JOIN LATERAL (
                    SELECT * FROM app.ai_analysis WHERE rma_id = c.rma_id
                    ORDER BY created_at DESC, poll_started_at DESC, analysis_id DESC LIMIT 1
                ) a ON TRUE
                WHERE c.rma_id = :rmaId
                """).param("rmaId", rmaId).query(Snapshot.class).optional();
    }

    public List<String> findDueAnalyses() {
        return jdbcClient.sql("""
                SELECT analysis_id FROM app.ai_analysis
                WHERE analysis_state IN ('QUEUED', 'RUNNING') AND next_poll_at <= CURRENT_TIMESTAMP
                ORDER BY next_poll_at, analysis_id LIMIT 50
                """).query(String.class).list();
    }

    public Optional<Pending> lockDueAnalysis(String analysisId) {
        // 여러 실행기가 같은 작업을 골라도 한 번에 하나만 조회하고 저장하게 함
        return jdbcClient.sql("""
                SELECT analysis_id, rma_id,
                       poll_started_at + (CASE WHEN mode = 'A' THEN 100 ELSE 40 END) * INTERVAL '1 second'
                           <= CURRENT_TIMESTAMP AS timed_out
                FROM app.ai_analysis
                WHERE analysis_id = :analysisId AND analysis_state IN ('QUEUED', 'RUNNING')
                    AND next_poll_at <= CURRENT_TIMESTAMP
                FOR UPDATE SKIP LOCKED
                """).param("analysisId", analysisId).query(Pending.class).optional();
    }

    public void reschedule(String analysisId) {
        // 로컬 시연의 고정 시계와 별개로 실제 경과 시간에 맞춰 다음 조회를 예약함
        jdbcClient.sql("""
                UPDATE app.ai_analysis SET next_poll_at = clock_timestamp() + poll_interval_ms * INTERVAL '1 millisecond'
                WHERE analysis_id = :analysisId
                """).param("analysisId", analysisId).update();
    }

    public void save(Update update) {
        jdbcClient.sql("""
                UPDATE app.ai_analysis
                SET analysis_state = :state, as_of = COALESCE(:asOf, as_of),
                    progress_json = COALESCE(CAST(:progress AS jsonb), progress_json),
                    result_json = CAST(:result AS jsonb), trace_json = CAST(:trace AS jsonb),
                    warnings_json = COALESCE(CAST(:warnings AS jsonb), warnings_json),
                    error_json = CAST(:error AS jsonb), langsmith_run_id = :langsmithRunId,
                    completed_at = :completedAt, updated_at = :updatedAt,
                    next_poll_at = clock_timestamp() + poll_interval_ms * INTERVAL '1 millisecond'
                WHERE analysis_id = :analysisId
                """).paramSource(update).update();
    }

    public Optional<String> findLatestQaLevel(String rmaId) {
        return jdbcClient.sql("""
                SELECT analysis_level FROM pcb.qa_triage WHERE rma_id = :rmaId
                ORDER BY decided_at DESC, triage_id DESC LIMIT 1
                """).param("rmaId", rmaId).query(String.class).optional();
    }

    public void createWaitingDecision(String rmaId, String analysisId, String level) {
        jdbcClient.sql("""
                INSERT INTO app.cause_decision (rma_id, analysis_id, approval_status, analysis_level)
                VALUES (:rmaId, :analysisId, 'WAITING', :level)
                ON CONFLICT (analysis_id) DO NOTHING
                """).param("rmaId", rmaId).param("analysisId", analysisId).param("level", level).update();
    }

    public record Snapshot(String rmaId, String analysisId, String analysisState, OffsetDateTime asOf, String mode,
                           String progress, String result, String trace, String warnings, String error,
                           String langsmithRunId, OffsetDateTime updatedAt) {
    }

    public record Pending(String analysisId, String rmaId, boolean timedOut) {
    }

    public record ReanalysisInput(String rawText, String intakeState, long claimedDefectQty, boolean triaged,
                                  boolean decisionFinalized) {
    }

    public record ActiveAnalysis(String analysisId, String analysisState) {
    }

    public record Update(String analysisId, String state, OffsetDateTime asOf, String progress, String result,
                         String trace, String warnings, String error, String langsmithRunId,
                         OffsetDateTime completedAt, OffsetDateTime updatedAt) {
    }
}
