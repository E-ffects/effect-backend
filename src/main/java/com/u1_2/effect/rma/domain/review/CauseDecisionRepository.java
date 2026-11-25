package com.u1_2.effect.rma.domain.review;

import com.u1_2.effect.rma.domain.RmaHistoryOrder;
import com.u1_2.effect.rma.domain.analysis.AnalysisResultStates;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CauseDecisionRepository {

    private final JdbcClient jdbcClient;

    public Optional<Target> findLatestCompleted(String rmaId) {
        return jdbcClient.sql("""
                SELECT analysis.analysis_id, analysis.result_json::text AS result, d.approval_status, d.analysis_level
                FROM app.ai_analysis analysis LEFT JOIN app.cause_decision d ON d.analysis_id = analysis.analysis_id
                WHERE analysis.rma_id = :rmaId AND analysis.analysis_state IN (:resultStates)
                ORDER BY %s LIMIT 1
                """.formatted(RmaHistoryOrder.ANALYSIS)).param("rmaId", rmaId)
                .param("resultStates", AnalysisResultStates.WITH_RESULT).query(Target.class).optional();
    }

    public void save(Decision row) {
        // 승인·확인과 사례 갱신 예약을 같이 저장해 커밋 직후 서버가 재시작해도 작업이 남음
        jdbcClient.sql("""
                INSERT INTO app.cause_decision (rma_id, analysis_id, approval_status, analysis_level,
                    candidates_json, comment, decided_by, decided_at, refresh_status, next_refresh_at)
                VALUES (:rmaId, :analysisId, :status, :level, CAST(:candidates AS jsonb), :comment, :editor, :decidedAt,
                    CASE WHEN :refresh THEN 'PENDING' END, CASE WHEN :refresh THEN CURRENT_TIMESTAMP END)
                ON CONFLICT (analysis_id) DO UPDATE SET approval_status = EXCLUDED.approval_status,
                    analysis_level = EXCLUDED.analysis_level, candidates_json = EXCLUDED.candidates_json,
                    comment = EXCLUDED.comment, decided_by = EXCLUDED.decided_by, decided_at = EXCLUDED.decided_at,
                    refresh_status = EXCLUDED.refresh_status, next_refresh_at = EXCLUDED.next_refresh_at
                """).paramSource(row).update();
    }

    public List<String> findDueRefreshes() {
        return jdbcClient.sql("""
                SELECT analysis_id FROM app.cause_decision
                WHERE refresh_status = 'PENDING' AND next_refresh_at <= CURRENT_TIMESTAMP
                ORDER BY next_refresh_at, id LIMIT 25
                """).query(String.class).list();
    }

    public Optional<Refresh> claimDueRefresh(String analysisId, int leaseSeconds) {
        // 요청 처리 직후와 스케줄러가 같은 분석을 집어도 한 쪽만 호출함. 다음 갱신 시각을 선점 만료 시각으로 미룸
        return jdbcClient.sql("""
                UPDATE app.cause_decision SET next_refresh_at = clock_timestamp() + :lease * INTERVAL '1 second'
                WHERE analysis_id = :analysisId AND refresh_status = 'PENDING' AND next_refresh_at <= CURRENT_TIMESTAMP
                RETURNING analysis_id, rma_id, approval_status AS decision, decided_at, refresh_attempts AS attempts,
                    next_refresh_at AS claimed_until
                """).param("analysisId", analysisId).param("lease", leaseSeconds).query(Refresh.class).optional();
    }

    public boolean updateRefresh(String analysisId, String status, int attempts, OffsetDateTime claimedUntil) {
        // 호출하는 동안 판정이 다시 저장되면 갱신 예약이 새로 잡히므로 이전 판정의 결과로 덮어쓰지 않음
        return jdbcClient.sql("""
                UPDATE app.cause_decision SET refresh_status = :status, refresh_attempts = :attempts,
                    next_refresh_at = CASE WHEN :status = 'PENDING' THEN clock_timestamp() + INTERVAL '1 minute' END
                WHERE analysis_id = :analysisId AND refresh_status = 'PENDING' AND next_refresh_at = :claimedUntil
                """).param("analysisId", analysisId).param("status", status).param("attempts", attempts)
                .param("claimedUntil", claimedUntil).update() == 1;
    }

    public record Target(String analysisId, String result, String approvalStatus, String analysisLevel) {
    }

    public record Decision(String rmaId, String analysisId, String status, String level, String candidates,
                           String comment, String editor, OffsetDateTime decidedAt, boolean refresh) {
    }

    public record Refresh(String analysisId, String rmaId, String decision, OffsetDateTime decidedAt, int attempts,
                          OffsetDateTime claimedUntil) {
    }
}
