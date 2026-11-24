package com.u1_2.effect.alert.domain;

import com.u1_2.effect.global.time.KoreaDateRange;
import com.u1_2.effect.global.time.KoreaTime;
import com.u1_2.effect.rma.domain.RmaHistoryOrder;
import com.u1_2.effect.rma.domain.RmaSeverity;
import java.time.Clock;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AlertSourceRepository {

    private static final String INSERT_EVENTS = """
            INSERT INTO app.alert (source_key, type, rma_id, corrective_action_id, target_label,
                                   reason, check_items, created_at, pattern_id)
            SELECT e.source_key, e.type, e.rma_id, e.corrective_action_id,
                   concat_ws(' · ', c.reported_part_no, e.rma_id), e.reason, e.check_items, e.event_at, e.pattern_id
            FROM events e JOIN pcb.rma_claim c USING (rma_id)
            WHERE NOT EXISTS (SELECT 1 FROM app.alert a WHERE a.source_key = e.source_key)
            ORDER BY e.event_at, e.source_key
            ON CONFLICT (source_key) DO NOTHING
            """;

    private final JdbcClient jdbcClient;
    private final Clock appClock;

    public void refresh() {
        // 여러 서버가 동시에 실행돼도 하나의 트랜잭션만 이벤트를 추가해 ID 순서를 유지함
        boolean locked = jdbcClient.sql("SELECT pg_try_advisory_xact_lock(40215, 1)").query(Boolean.class).single();
        if (!locked) {
            return;
        }
        var sources = jdbcClient.sql("""
                SELECT (to_regclass('pcb.qa_triage') IS NOT NULL AND to_regclass('pcb.rma_claim') IS NOT NULL
                    AND to_regclass('pcb.rma_item') IS NOT NULL AND to_regclass('pcb.recurrence_review') IS NOT NULL)
                        AS case_events,
                    (to_regclass('pcb.symptom_assessment') IS NOT NULL AND to_regclass('pcb.symptom_definition') IS NOT NULL
                    AND to_regclass('pcb.corrective_action') IS NOT NULL AND to_regclass('pcb.investigation_claim') IS NOT NULL)
                        AS aggregates
                """).query(SourceAvailability.class).single();
        if (sources.caseEvents()) {
            insertCaseEvents();
            if (sources.aggregates()) {
                insertAggregateEvents();
            }
        }
    }

    private void insertCaseEvents() {
        // 기존 원본을 다시 읽거나 배치가 재실행돼도 같은 RMA·대책 알림은 한 번만 추가함
        jdbcClient.sql("""
                WITH critical AS (
                    SELECT DISTINCT ON (q.rma_id) q.rma_id, q.decided_at::timestamptz AS event_at
                    FROM pcb.qa_triage q
                    WHERE q.severity >= :critical
                    ORDER BY q.rma_id, q.decided_at::timestamptz, q.triage_id
                ), suspected AS (
                    SELECT DISTINCT ON (r.corrective_action_id, i.rma_id) r.corrective_action_id, i.rma_id,
                           r.reviewed_at::timestamptz AS event_at
                    FROM pcb.recurrence_review r JOIN pcb.rma_item i USING (rma_item_id)
                    WHERE r.verdict = 'PENDING_CAUSE_REVIEW'
                    ORDER BY r.corrective_action_id, i.rma_id, r.reviewed_at::timestamptz, r.review_id
                ), events AS (
                    SELECT 'critical:' || rma_id AS source_key, '고위험' AS type, rma_id,
                           CAST(NULL AS text) AS corrective_action_id, event_at,
                           '품질 판정에서 고위험으로 기록됨' AS reason,
                           '접수 상세의 QA 판정과 근거 확인' AS check_items, CAST(NULL AS text) AS pattern_id
                    FROM critical
                    UNION ALL
                    SELECT 'recurrence:' || corrective_action_id || ':' || rma_id, '재발 의심', rma_id,
                           corrective_action_id, event_at, '재발 검토에서 원인 확인 대기로 기록됨',
                           '관련 대책과 원인 일치 여부 확인', NULL
                    FROM suspected
                    UNION ALL
                    SELECT 'spread:' || rma_id, '확산형', rma_id, NULL, decided_at,
                           '품질 담당자가 확산형 재발로 확정함', '관련 접수 범위와 재발 방지 대책 확인', NULL
                    FROM app.rma_recurrence_decision
                    WHERE decision = 'SPREAD_RECURRENCE'
                    UNION ALL
                    SELECT 'cause-outcome:' || analysis_id, '원인 판정 결과', rma_id, NULL, decided_at,
                           CASE approval_status
                               WHEN 'APPROVED' THEN '원인 후보가 승인됨'
                               WHEN 'CONFIRMED' THEN '원인 후보 확인이 완료됨'
                               WHEN 'REJECTED' THEN '원인 후보가 반려됨'
                           END, '접수 상세에서 원인 판정 결과 확인', NULL
                    FROM app.cause_decision
                    WHERE approval_status IN ('APPROVED', 'CONFIRMED', 'REJECTED') AND decided_at IS NOT NULL
                    UNION ALL
                    SELECT 'analysis-outcome:' || analysis_id, '분석 결과', rma_id, NULL, completed_at,
                           CASE analysis_state
                               WHEN 'SUCCEEDED' THEN 'AI 분석이 완료됨'
                               WHEN 'PARTIAL' THEN 'AI 분석이 일부 완료됨'
                               WHEN 'FAILED' THEN 'AI 분석에 실패함'
                           END, '접수 상세에서 분석 상태와 결과 확인', NULL
                    FROM app.ai_analysis
                    WHERE analysis_state IN ('SUCCEEDED', 'PARTIAL', 'FAILED') AND completed_at IS NOT NULL
                )
                """ + INSERT_EVENTS).param("critical", RmaSeverity.CRITICAL.minimum()).update();
    }

    private void insertAggregateEvents() {
        OffsetDateTime now = OffsetDateTime.now(appClock);
        // 대시보드와 같은 서울 날짜 범위를 쓰고 당일에는 현재 시각까지의 접수만 셈
        var period = KoreaDateRange.resolve(null, null, KoreaTime.inSeoul(now).toLocalDate());

        // 접수 하나에 분류나 품목이 여러 개 있어도 목록과 같은 최신 분류로 한 번만 셈
        // 반복 재발은 같은 조사에 품목·대책·원본이 중복돼도 접수 기준으로 한 번만 셈
        jdbcClient.sql("""
                WITH classified AS (
                    SELECT DISTINCT ON (c.rma_id) c.rma_id, c.reported_part_no, d.major_code, d.major_name,
                           c.opened_at::timestamptz AS opened_at
                    FROM pcb.rma_claim c JOIN pcb.rma_item i USING (rma_id)
                    JOIN pcb.symptom_assessment symptom USING (rma_item_id)
                    JOIN pcb.symptom_definition d ON d.minor_code = symptom.primary_label
                    WHERE c.opened_at::timestamptz BETWEEN :baselineFrom AND :now
                    ORDER BY c.rma_id, %s
                ), spikes AS (
                    SELECT (array_agg(rma_id ORDER BY opened_at DESC, rma_id DESC))[1] AS rma_id,
                           (array_agg(major_name ORDER BY opened_at DESC, rma_id DESC))[1] AS major_name,
                           major_code, count(*) FILTER (WHERE opened_at >= :recentFrom)
                               - count(*) FILTER (WHERE opened_at < :recentFrom) AS increase
                    FROM classified
                    GROUP BY reported_part_no, major_code
                ), recurrence_events AS (
                    SELECT a.investigation_id, i.rma_id, r.reviewed_at::timestamptz AS event_at
                    FROM pcb.recurrence_review r JOIN pcb.rma_item i USING (rma_item_id)
                    JOIN pcb.corrective_action a USING (corrective_action_id)
                    LEFT JOIN app.rma_recurrence_decision d ON d.rma_id = i.rma_id
                    WHERE r.verdict = 'RECURRENCE' AND d.decision IS DISTINCT FROM 'NEW'
                    UNION ALL
                    SELECT i.investigation_id, d.rma_id, d.decided_at
                    FROM app.rma_recurrence_decision d JOIN pcb.investigation_claim i USING (rma_id)
                    WHERE d.decision IN ('RECURRENCE', 'SPREAD_RECURRENCE')
                ), distinct_recurrences AS (
                    SELECT investigation_id, rma_id, min(event_at) AS event_at
                    FROM recurrence_events GROUP BY investigation_id, rma_id
                ), ranked_recurrences AS (
                    SELECT investigation_id, rma_id, event_at,
                           row_number() OVER (PARTITION BY investigation_id ORDER BY event_at, rma_id) AS recurrence_number
                    FROM distinct_recurrences
                ), events AS (
                    SELECT 'spike:' || rma_id || ':' || major_code AS source_key, '급증' AS type, rma_id,
                           NULL::text AS corrective_action_id, CAST(:now AS timestamptz) AS event_at,
                           format('같은 품번과 불량 대분류(%%s) 접수가 직전 30일보다 %%s건 증가함',
                                  major_name, increase) AS reason,
                           '최근 30일 접수 추이와 불량 분류 확인' AS check_items, NULL::text AS pattern_id
                    FROM spikes WHERE increase >= 3
                    UNION ALL
                    SELECT 'repeat:' || investigation_id, '반복 재발', rma_id, NULL, event_at,
                           '같은 조사에 속한 서로 다른 접수 2건에서 재발이 확인됨',
                           '패턴 상세에서 두 접수의 원인과 후속 대책 확인', investigation_id
                    FROM ranked_recurrences WHERE recurrence_number = 2
                )
                """.formatted(RmaHistoryOrder.SYMPTOM) + INSERT_EVENTS)
                .param("now", now).param("recentFrom", period.start()).param("baselineFrom", period.previousStart()).update();
    }

    private record SourceAvailability(boolean caseEvents, boolean aggregates) {
    }
}
