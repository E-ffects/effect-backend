CREATE SCHEMA IF NOT EXISTS pcb;
CREATE TABLE pcb.rma_claim (rma_id TEXT PRIMARY KEY, reported_part_no TEXT, opened_at TEXT);
CREATE TABLE pcb.rma_item (rma_item_id TEXT PRIMARY KEY, rma_id TEXT, verified_defect_qty INTEGER);
CREATE TABLE pcb.loss_ledger (investigation_id TEXT, occurred_at TEXT, amount_krw BIGINT);
CREATE TABLE pcb.investigation (investigation_id TEXT PRIMARY KEY, title TEXT, analysis_level TEXT);
CREATE TABLE pcb.investigation_claim (investigation_id TEXT, rma_id TEXT, linked_at TEXT);
CREATE TABLE pcb.symptom_definition (minor_code TEXT PRIMARY KEY, major_name TEXT);
CREATE TABLE pcb.symptom_assessment (assessment_id TEXT PRIMARY KEY, rma_item_id TEXT, primary_label TEXT, created_at TEXT);
CREATE TABLE pcb.qa_triage (triage_id TEXT PRIMARY KEY, rma_id TEXT, severity INTEGER, decided_at TEXT);
CREATE TABLE pcb.cause_master (cause_code TEXT PRIMARY KEY, cause_name TEXT);
CREATE TABLE pcb.cause_assessment (
    cause_assessment_id TEXT PRIMARY KEY, investigation_id TEXT, cause_code TEXT, certainty_status TEXT, approved_at TEXT
);
CREATE TABLE pcb.claim_cause (rma_item_id TEXT, cause_assessment_id TEXT, applicability TEXT, decided_at TEXT);
CREATE TABLE pcb.corrective_action (
    corrective_action_id TEXT PRIMARY KEY, investigation_id TEXT, action_category TEXT, implemented_at TEXT
);
CREATE TABLE pcb.effectiveness_check (check_id TEXT PRIMARY KEY, corrective_action_id TEXT, result TEXT, checked_at TEXT);
CREATE TABLE pcb.recurrence_review (
    review_id TEXT PRIMARY KEY, rma_item_id TEXT, corrective_action_id TEXT, verdict TEXT, reviewed_at TEXT
);
CREATE VIEW pcb.loss_by_investigation AS
    SELECT investigation_id, sum(amount_krw) total_krw FROM pcb.loss_ledger GROUP BY investigation_id;

INSERT INTO pcb.rma_claim VALUES
    ('RMA-P', 'PCB-A', '2025-12-01T23:59:59+09:00'),
    ('RMA-A', 'PCB-A', '2025-12-02T00:00:00+09:00'),
    ('RMA-B', 'PCB-A', '2025-12-30T15:00:00Z'),
    ('RMA-C', 'PCB-C', '2025-12-31T15:00:00+09:00'),
    ('RMA-D', 'PCB-D', '2025-12-31T16:00:00+09:00'),
    ('RMA-F', 'PCB-A', '2025-12-31T15:00:00Z');

INSERT INTO pcb.rma_item VALUES
    ('A1', 'RMA-A', 10), ('A2', 'RMA-A', 5), ('B1', 'RMA-B', 5), ('C1', 'RMA-C', NULL);

INSERT INTO pcb.investigation VALUES ('INV-A', '납땜 불량 조사', 'L3'), ('INV-C', '회로 불량 조사', 'L2');

-- 같은 RMA의 중복 연결과 동일 조사에 속한 기간 밖 접수도 들어 있음
INSERT INTO pcb.investigation_claim VALUES
    ('INV-A', 'RMA-P', '2025-12-01T23:59:59+09:00'),
    ('INV-A', 'RMA-A', '2025-12-02T01:00:00+09:00'), ('INV-A', 'RMA-A', '2025-12-02T02:00:00+09:00'),
    ('INV-A', 'RMA-B', '2025-12-31T00:10:00+09:00'),
    ('INV-C', 'RMA-C', '2025-12-31T15:10:00+09:00'),
    ('INV-A', 'RMA-F', '2026-01-01T00:01:00+09:00');

INSERT INTO pcb.loss_ledger VALUES
    ('INV-A', '2025-12-01T23:59:59+09:00', 500),
    ('INV-A', '2025-12-02T00:00:00+09:00', 1000),
    ('INV-C', '2025-12-31T23:59:59+09:00', 250),
    ('INV-A', '2025-12-31T15:00:00Z', 750);

INSERT INTO pcb.symptom_definition VALUES ('D1', '납땜'), ('D2', '전기');
INSERT INTO pcb.symptom_assessment VALUES
    ('SA1', 'A1', 'D2', '2025-12-02T01:00:00+09:00'), ('SA2', 'A2', 'D1', '2025-12-02T01:00:00+09:00'),
    ('SB1', 'B1', 'D1', '2025-12-31T01:00:00+09:00'), ('SC1', 'C1', 'D2', '2025-12-31T16:00:00+09:00');
INSERT INTO pcb.qa_triage VALUES ('TB1', 'RMA-B', 9, '2025-12-31T01:00:00+09:00');
INSERT INTO pcb.cause_master VALUES ('CAUSE-A', '접합 이상');
INSERT INTO pcb.cause_assessment VALUES
    ('CA-A', 'INV-A', 'CAUSE-A', 'CONFIRMED', '2025-12-31T10:00:00+09:00');
INSERT INTO pcb.claim_cause VALUES
    ('A1', 'CA-A', 'APPLIES', '2025-12-31T10:00:00+09:00'),
    ('A2', 'CA-A', 'APPLIES', '2025-12-31T10:00:00+09:00'),
    ('B1', 'CA-A', 'APPLIES', '2026-01-01T00:00:00+09:00');

INSERT INTO pcb.corrective_action VALUES
    ('ACT-A', 'INV-A', 'PROCESS_CONTROL', '2025-12-02T15:00:00Z'),
    ('ACT-C', 'INV-C', 'PROCESS_CONTROL', '2025-12-20T00:00:00+09:00');
INSERT INTO pcb.effectiveness_check VALUES
    ('E1', 'ACT-A', 'EFFECTIVE', '2025-12-10T00:00:00+09:00'),
    ('E2', 'ACT-A', 'NOT_EFFECTIVE', '2026-01-01T00:00:00+09:00'),
    ('E3', 'ACT-C', 'EFFECTIVE', '2025-12-25T00:00:00+09:00');

INSERT INTO pcb.recurrence_review VALUES
    ('R1', 'B1', 'ACT-A', 'PRE_ACTION_PRODUCT', '2025-12-31T01:00:00+09:00'),
    ('R2', 'B1', 'ACT-A', 'PENDING_CAUSE_REVIEW', '2025-12-31T02:00:00+09:00'),
    ('R3', 'C1', 'ACT-C', 'RECURRENCE', '2025-12-31T20:00:00+09:00');
