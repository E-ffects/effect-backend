CREATE SCHEMA IF NOT EXISTS pcb;
CREATE TABLE pcb.rma_claim (rma_id TEXT PRIMARY KEY, reported_part_no TEXT, opened_at TEXT, cs_status TEXT);
CREATE TABLE pcb.qa_triage (triage_id TEXT PRIMARY KEY, rma_id TEXT, severity INTEGER, decided_at TEXT);
INSERT INTO pcb.rma_claim VALUES
    ('RMA-REPORT', 'PCB-A', '2025-12-09T15:00:00Z', 'OPEN'),
    ('RMA-EVIDENCE', 'PCB-B', '2025-12-01T10:00:00+09:00', 'CLOSED'),
    ('RMA-UNANALYSED', 'PCB-C', '2025-12-01T10:00:00+09:00', 'OPEN');
INSERT INTO pcb.qa_triage VALUES ('T-REPORT', 'RMA-REPORT', 8, '2025-12-10T12:00:00+09:00');
INSERT INTO users (id, username, password, name, role, created_at, updated_at)
VALUES (91004, 'report-qa', 'unused', '품질 검토자', 'QUALITY', LOCALTIMESTAMP, LOCALTIMESTAMP);

INSERT INTO app.ai_analysis (analysis_id, rma_id, mode, analysis_state, created_at, result_json) VALUES
('analysis-report-a', 'RMA-REPORT', 'B', 'SUCCEEDED', '2025-12-11T10:00:00+09:00', '{
   "classification":{"minor_name":"단선"},
   "cause_candidates":[{"cause_name":"연결 불량","explanation":"추가 시험 필요","evidence":[
     {"ref_table":"rma_claim","ref_id":"RMA-EVIDENCE"},
     {"ref_table":"rma_claim","ref_id":"RMA-NOT-IN-DB"}]}],
   "similar_cases":[{"rma_id":"RMA-EVIDENCE","minor_name":"단선"},{"rma_id":"RMA-NOT-IN-DB"}],
   "drafts":{"customer_reply":"시료 회수 일정을 확인합니다.","improvement":{"process":[{"action":"공정 조건 점검"}]}}
}'::jsonb);
