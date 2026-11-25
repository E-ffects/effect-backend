package com.u1_2.effect.rma.domain.analysis;

import com.u1_2.effect.rma.domain.RmaHistoryOrder;
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

    public void insertAnalysis(AnalysisRow row) {
        jdbcClient.sql("""
                INSERT INTO app.ai_analysis (analysis_id, rma_id, mode, analysis_state, created_at, completed_at,
                    error_json, updated_at, poll_interval_ms, next_poll_at)
                VALUES (:analysisId, :rmaId, :mode, :state, :createdAt, :completedAt, CAST(:error AS jsonb),
                    :updatedAt, :pollIntervalMs, CURRENT_TIMESTAMP + (:pollIntervalMs * INTERVAL '1 millisecond'))
                ON CONFLICT (analysis_id) DO NOTHING
                """).paramSource(row).update();
    }

    public String findRegisteredState(String rmaId, String analysisId) {
        return jdbcClient.sql("""
                SELECT analysis_state FROM app.ai_analysis WHERE rma_id = :rmaId AND analysis_id = :analysisId
                """).param("rmaId", rmaId).param("analysisId", analysisId).query(String.class).single();
    }

    public boolean belongsToRma(String analysisId, String rmaId) {
        return jdbcClient.sql("""
                SELECT EXISTS (SELECT 1 FROM app.ai_analysis WHERE analysis_id = :analysisId AND rma_id = :rmaId)
                """).param("analysisId", analysisId).param("rmaId", rmaId).query(Boolean.class).single();
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
                SELECT analysis_id, analysis_state FROM app.ai_analysis analysis
                WHERE rma_id = :rmaId AND analysis_state IN ('QUEUED', 'RUNNING')
                ORDER BY %s LIMIT 1
                """.formatted(RmaHistoryOrder.ANALYSIS)).param("rmaId", rmaId).query(ActiveAnalysis.class).optional();
    }

    public Optional<Snapshot> findLatest(String rmaId) {
        return findLatest(rmaId, false);
    }

    public Optional<Snapshot> findLatest(String rmaId, boolean includeDecision) {
        // 최신 분석과 같은 분석 번호의 판정만 조회해 이전 분석의 승인·반려가 섞이지 않게 함
        return jdbcClient.sql("""
                SELECT c.rma_id, a.analysis_id, a.analysis_state, a.as_of, a.mode,
                       a.progress_json::text AS progress, a.result_json::text AS result,
                       a.trace_json::text AS trace, a.warnings_json::text AS warnings,
                       a.error_json::text AS error, a.langsmith_run_id,
                       COALESCE(a.updated_at, a.completed_at, a.created_at) AS updated_at,
                       d.approval_status,
                       CASE WHEN d.approval_status IN ('APPROVED', 'CONFIRMED')
                           THEN jsonb_path_query_first(d.candidates_json, '$[*] ? (@.status == "ADOPTED").cause_code') #>> '{}'
                       END AS adopted_cause_code
                FROM pcb.rma_claim c
                LEFT JOIN LATERAL (
                    SELECT * FROM app.ai_analysis analysis WHERE rma_id = c.rma_id
                    ORDER BY %s LIMIT 1
                ) a ON TRUE
                LEFT JOIN app.cause_decision d ON :includeDecision AND d.analysis_id = a.analysis_id AND d.rma_id = c.rma_id
                WHERE c.rma_id = :rmaId
                """.formatted(RmaHistoryOrder.ANALYSIS)).param("rmaId", rmaId)
                .param("includeDecision", includeDecision).query(Snapshot.class).optional();
    }

    public List<String> findDueAnalyses() {
        return jdbcClient.sql("""
                SELECT analysis_id FROM app.ai_analysis
                WHERE analysis_state IN ('QUEUED', 'RUNNING') AND next_poll_at <= CURRENT_TIMESTAMP
                ORDER BY next_poll_at, analysis_id LIMIT 50
                """).query(String.class).list();
    }

    public Optional<Pending> claimDueAnalysis(String analysisId, int leaseSeconds) {
        // 조회 예정 시각을 선점 만료 시각으로 미루고 바로 커밋함. 같은 작업은 만료 전까지 다른 실행기가 집지 않음
        return jdbcClient.sql("""
                UPDATE app.ai_analysis SET next_poll_at = clock_timestamp() + :lease * INTERVAL '1 second'
                WHERE analysis_id = :analysisId AND analysis_state IN ('QUEUED', 'RUNNING')
                    AND next_poll_at <= CURRENT_TIMESTAMP
                RETURNING analysis_id, rma_id,
                    poll_started_at + (CASE WHEN mode = 'A' THEN 100 ELSE 40 END) * INTERVAL '1 second'
                        <= CURRENT_TIMESTAMP AS timed_out,
                    next_poll_at AS claimed_until
                """).param("analysisId", analysisId).param("lease", leaseSeconds).query(Pending.class).optional();
    }

    public boolean reschedule(String analysisId, OffsetDateTime claimedUntil) {
        // 로컬 시연의 고정 시계와 별개로 실제 경과 시간에 맞춰 다음 조회를 예약함
        return jdbcClient.sql("""
                UPDATE app.ai_analysis SET next_poll_at = clock_timestamp() + poll_interval_ms * INTERVAL '1 millisecond'
                WHERE analysis_id = :analysisId AND next_poll_at = :claimedUntil
                """).param("analysisId", analysisId).param("claimedUntil", claimedUntil).update() == 1;
    }

    public boolean save(Update update) {
        // 선점한 실행기만 저장함. 선점 시각이 바뀌었거나 이미 끝난 작업이면 늦게 온 응답을 버림
        return jdbcClient.sql("""
                UPDATE app.ai_analysis
                SET analysis_state = :state, as_of = COALESCE(:asOf, as_of),
                    progress_json = COALESCE(CAST(:progress AS jsonb), progress_json),
                    result_json = CAST(:result AS jsonb), trace_json = CAST(:trace AS jsonb),
                    warnings_json = COALESCE(CAST(:warnings AS jsonb), warnings_json),
                    error_json = CAST(:error AS jsonb), langsmith_run_id = :langsmithRunId,
                    completed_at = :completedAt, updated_at = :updatedAt,
                    next_poll_at = clock_timestamp() + poll_interval_ms * INTERVAL '1 millisecond'
                WHERE analysis_id = :analysisId AND next_poll_at = :claimedUntil
                    AND analysis_state IN ('QUEUED', 'RUNNING')
                """).paramSource(update).update() == 1;
    }

    public Optional<OffsetDateTime> claimRegistration(String rmaId, int leaseSeconds) {
        // FastAPI 등록을 기다리는 동안 같은 RMA의 등록 요청이 한 번만 나가게 함. 만료된 선점은 넘겨받음
        return jdbcClient.sql("""
                INSERT INTO app.analysis_registration (rma_id, lease_until)
                VALUES (:rmaId, clock_timestamp() + :lease * INTERVAL '1 second')
                ON CONFLICT (rma_id) DO UPDATE SET lease_until = EXCLUDED.lease_until
                WHERE app.analysis_registration.lease_until <= clock_timestamp()
                RETURNING lease_until
                """).param("rmaId", rmaId).param("lease", leaseSeconds).query(OffsetDateTime.class).optional();
    }

    public void releaseRegistration(String rmaId, OffsetDateTime leaseUntil) {
        jdbcClient.sql("DELETE FROM app.analysis_registration WHERE rma_id = :rmaId AND lease_until = :leaseUntil")
                .param("rmaId", rmaId).param("leaseUntil", leaseUntil).update();
    }

    public Optional<String> findLatestQaLevel(String rmaId) {
        return jdbcClient.sql("""
                SELECT analysis_level FROM pcb.qa_triage triage WHERE rma_id = :rmaId
                ORDER BY %s LIMIT 1
                """.formatted(RmaHistoryOrder.TRIAGE)).param("rmaId", rmaId).query(String.class).optional();
    }

    public void createWaitingDecision(String rmaId, String analysisId, String level) {
        jdbcClient.sql("""
                INSERT INTO app.cause_decision (rma_id, analysis_id, approval_status, analysis_level)
                VALUES (:rmaId, :analysisId, 'WAITING', :level)
                ON CONFLICT (analysis_id) DO NOTHING
                """).param("rmaId", rmaId).param("analysisId", analysisId).param("level", level).update();
    }

    public record AnalysisRow(String analysisId, String rmaId, String mode, String state,
                              OffsetDateTime createdAt, OffsetDateTime updatedAt, OffsetDateTime completedAt,
                              String error, int pollIntervalMs) {
    }

    public record Snapshot(String rmaId, String analysisId, String analysisState, OffsetDateTime asOf, String mode,
                           String progress, String result, String trace, String warnings, String error,
                           String langsmithRunId, OffsetDateTime updatedAt, String approvalStatus, String adoptedCauseCode) {
    }

    public record Pending(String analysisId, String rmaId, boolean timedOut, OffsetDateTime claimedUntil) {
    }

    public record ReanalysisInput(String rawText, String intakeState, long claimedDefectQty, boolean triaged,
                                  boolean decisionFinalized) {
    }

    public record ActiveAnalysis(String analysisId, String analysisState) {
    }

    public record Update(String analysisId, String state, OffsetDateTime asOf, String progress, String result,
                         String trace, String warnings, String error, String langsmithRunId,
                         OffsetDateTime completedAt, OffsetDateTime updatedAt, OffsetDateTime claimedUntil) {
    }
}
