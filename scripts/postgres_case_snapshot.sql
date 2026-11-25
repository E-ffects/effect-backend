-- 재적재·복원 연습 전후에 같은 접수 번호를 넣어 업무 기록의 건수와 지문을 비교함
\set ON_ERROR_STOP on
BEGIN ISOLATION LEVEL REPEATABLE READ, READ ONLY;
SET LOCAL TIME ZONE 'UTC';

SELECT EXISTS (SELECT FROM pcb.rma_claim WHERE rma_id = :'rma_id') AS case_exists
\gset
\if :case_exists
\else
    DO $$ BEGIN RAISE EXCEPTION 'RMA for preservation check does not exist'; END $$;
\endif

WITH items AS (
    SELECT * FROM pcb.rma_item WHERE rma_id = :'rma_id'
), records AS (
    SELECT 'pcb.rma_claim' AS source, to_jsonb(c) AS payload FROM pcb.rma_claim c WHERE rma_id = :'rma_id'
    UNION ALL SELECT 'pcb.rma_item', to_jsonb(i) FROM items i
    UNION ALL SELECT 'pcb.symptom_assessment', to_jsonb(s) FROM pcb.symptom_assessment s JOIN items i USING (rma_item_id)
    UNION ALL SELECT 'pcb.qa_triage', to_jsonb(q) FROM pcb.qa_triage q WHERE rma_id = :'rma_id'
    UNION ALL SELECT 'pcb.claim_cause', to_jsonb(c) FROM pcb.claim_cause c JOIN items i USING (rma_item_id)
    UNION ALL SELECT 'pcb.investigation_claim', to_jsonb(c) FROM pcb.investigation_claim c WHERE rma_id = :'rma_id'
    UNION ALL SELECT 'pcb.communication', to_jsonb(c) FROM pcb.communication c WHERE rma_id = :'rma_id'
    UNION ALL SELECT 'pcb.service_action', to_jsonb(a) FROM pcb.service_action a JOIN items i USING (rma_item_id)
    UNION ALL SELECT 'app.intake', to_jsonb(i) FROM app.intake i WHERE rma_id = :'rma_id'
    UNION ALL SELECT 'app.attachment', to_jsonb(a) FROM app.attachment a WHERE rma_id = :'rma_id'
    UNION ALL SELECT 'app.ai_analysis', to_jsonb(a) FROM app.ai_analysis a WHERE rma_id = :'rma_id'
    UNION ALL SELECT 'app.cause_decision', to_jsonb(d) FROM app.cause_decision d WHERE rma_id = :'rma_id'
    UNION ALL SELECT 'app.communication_meta', to_jsonb(m) FROM app.communication_meta m WHERE rma_id = :'rma_id'
    UNION ALL SELECT 'app.triage_history', to_jsonb(t) FROM app.triage_history t WHERE rma_id = :'rma_id'
    UNION ALL SELECT 'app.report', to_jsonb(r) FROM app.report r WHERE rma_id = :'rma_id'
    UNION ALL SELECT 'app.rma_recurrence_decision', to_jsonb(r) FROM app.rma_recurrence_decision r WHERE rma_id = :'rma_id'
)
-- 원문, 회신 내용, 첨부 경로를 출력하지 않고 정렬한 행 지문으로 갱신·유실을 확인함
SELECT source, count(*) AS rows, md5(string_agg(row_hash, '' ORDER BY row_hash)) AS fingerprint
FROM (SELECT source, md5(payload::text) AS row_hash FROM records) hashed
GROUP BY source
ORDER BY source;

COMMIT;
