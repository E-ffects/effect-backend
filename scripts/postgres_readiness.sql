-- 운영자가 Flyway 적용 후 Spring과 AI의 실제 DB 역할을 지정해 읽기 전용으로 실행함
\set ON_ERROR_STOP on
BEGIN READ ONLY;

-- 접수 총건수에는 신규 접수도 포함되므로 v5 원본 ID 10,610개의 존재 여부를 따로 확인함
WITH expected_claims AS (
    SELECT 'RMA-' || lpad(n::text, 5, '0') AS rma_id
    FROM generate_series(1, 10610) AS n
), pcb_relations AS (
    SELECT c.oid
    FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'pcb' AND c.relkind IN ('r', 'p', 'v', 'm', 'f')
), spring_writes AS (
    VALUES ('pcb.rma_claim', 'INSERT'), ('pcb.rma_claim', 'UPDATE'),
           ('pcb.rma_item', 'INSERT'), ('pcb.symptom_assessment', 'INSERT'), ('pcb.symptom_assessment', 'DELETE'),
           ('pcb.qa_triage', 'INSERT'), ('pcb.qa_triage', 'UPDATE'), ('pcb.qa_triage', 'DELETE'),
           ('pcb.communication', 'INSERT'), ('pcb.service_action', 'INSERT'),
           ('pcb.cause_assessment', 'INSERT'), ('pcb.claim_cause', 'INSERT'),
           ('pcb.investigation', 'INSERT'), ('pcb.investigation_claim', 'INSERT'),
           ('pcb.corrective_action', 'INSERT'), ('pcb.corrective_action', 'UPDATE'),
           ('app.intake', 'INSERT'), ('app.intake', 'UPDATE'), ('app.attachment', 'INSERT'),
           ('app.ai_analysis', 'INSERT'), ('app.ai_analysis', 'UPDATE'),
           ('app.cause_decision', 'INSERT'), ('app.cause_decision', 'UPDATE'),
           ('app.communication_meta', 'INSERT'), ('app.triage_history', 'INSERT'),
           ('app.alert', 'INSERT'), ('app.alert_user_state', 'INSERT'), ('app.alert_user_state', 'UPDATE'),
           ('app.dashboard_layout', 'INSERT'), ('app.dashboard_layout', 'UPDATE'),
           ('app.report', 'INSERT'), ('app.report', 'UPDATE'), ('app.rma_recurrence_decision', 'INSERT'),
           ('app.corrective_action_registration', 'INSERT'), ('public.users', 'INSERT')
), checks AS (
    SELECT
        (SELECT count(*) FROM expected_claims v LEFT JOIN pcb.rma_claim c USING (rma_id)
         WHERE c.rma_id IS NULL) AS v5_missing,
        (SELECT count(*) FROM pcb.rma_claim) AS total_rmas,
        has_schema_privilege(:'spring_role', 'pcb', 'USAGE')
            AND has_schema_privilege(:'spring_role', 'app', 'USAGE')
            AND has_table_privilege(:'spring_role', 'pcb.rma_claim', 'SELECT')
            AND has_table_privilege(:'spring_role', 'app.ai_analysis', 'SELECT')
            AND has_table_privilege(:'spring_role', 'public.users', 'SELECT')
            AND NOT EXISTS (
                SELECT FROM spring_writes AS w(relation, operation)
                WHERE NOT has_table_privilege(:'spring_role', w.relation, w.operation)
            ) AS spring_data_access,
        has_schema_privilege(:'ai_role', 'pcb', 'USAGE')
            AND NOT has_schema_privilege(:'ai_role', 'pcb', 'CREATE')
            AND NOT has_schema_privilege(:'ai_role', 'app', 'USAGE')
            AND NOT has_table_privilege(:'ai_role', 'public.users', 'SELECT')
            AND NOT pg_has_role(:'ai_role', :'spring_role', 'MEMBER')
            AND NOT EXISTS (
                SELECT FROM pcb_relations p
                WHERE NOT has_table_privilege(:'ai_role', p.oid, 'SELECT')
                   OR has_table_privilege(:'ai_role', p.oid, 'INSERT, UPDATE, DELETE, TRUNCATE, TRIGGER')
            ) AS ai_pcb_read_only
)
SELECT *, v5_missing = 0 AND spring_data_access AND ai_pcb_read_only AS ready
FROM checks
\gset

\echo v5_missing=:v5_missing total_rmas=:total_rmas spring_data_access=:spring_data_access ai_pcb_read_only=:ai_pcb_read_only
\if :ready
    COMMIT;
\else
    DO $$ BEGIN RAISE EXCEPTION 'PostgreSQL readiness checks failed'; END $$;
\endif
