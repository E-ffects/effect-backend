package com.u1_2.effect.alert.domain;

import com.u1_2.effect.rma.domain.RmaSeverity;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AlertSourceRepository {

    private final JdbcClient jdbcClient;

    public void refresh() {
        // 여러 서버가 동시에 실행돼도 하나의 트랜잭션만 이벤트를 추가해 ID 순서를 유지함
        boolean locked = jdbcClient.sql("SELECT pg_try_advisory_xact_lock(40215, 1)").query(Boolean.class).single();
        if (!locked) {
            return;
        }
        boolean sourceReady = jdbcClient.sql("""
                SELECT to_regclass('pcb.qa_triage') IS NOT NULL AND to_regclass('pcb.rma_claim') IS NOT NULL
                   AND to_regclass('pcb.rma_item') IS NOT NULL AND to_regclass('pcb.recurrence_review') IS NOT NULL
                """).query(Boolean.class).single();
        if (!sourceReady) {
            return;
        }

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
                           '접수 상세의 QA 판정과 근거 확인' AS check_items
                    FROM critical
                    UNION ALL
                    SELECT 'recurrence:' || corrective_action_id || ':' || rma_id, '재발 의심', rma_id,
                           corrective_action_id, event_at, '재발 검토에서 원인 확인 대기로 기록됨',
                           '관련 대책과 원인 일치 여부 확인'
                    FROM suspected
                )
                INSERT INTO app.alert (source_key, type, rma_id, corrective_action_id, target_label, reason, check_items, created_at)
                SELECT e.source_key, e.type, e.rma_id, e.corrective_action_id, concat_ws(' · ', c.reported_part_no, e.rma_id),
                       e.reason, e.check_items, e.event_at
                FROM events e JOIN pcb.rma_claim c USING (rma_id)
                WHERE NOT EXISTS (SELECT 1 FROM app.alert a WHERE a.source_key = e.source_key)
                ORDER BY e.event_at, e.source_key
                ON CONFLICT (source_key) DO NOTHING
                """).param("critical", RmaSeverity.CRITICAL.minimum()).update();
    }
}
