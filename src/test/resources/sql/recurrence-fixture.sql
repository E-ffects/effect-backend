CREATE SCHEMA IF NOT EXISTS pcb;

CREATE TABLE pcb.investigation (investigation_id TEXT PRIMARY KEY, title TEXT NOT NULL);
CREATE TABLE pcb.investigation_claim (investigation_id TEXT, rma_id TEXT, linked_at TEXT);
CREATE TABLE pcb.rma_claim (rma_id TEXT PRIMARY KEY, opened_at TEXT NOT NULL);
CREATE TABLE pcb.rma_item (rma_item_id TEXT PRIMARY KEY, rma_id TEXT NOT NULL);
CREATE TABLE pcb.corrective_action (
    corrective_action_id TEXT PRIMARY KEY, investigation_id TEXT, action_role TEXT, action_category TEXT,
    description TEXT, owner_dept TEXT, cause_code TEXT, selected_at TEXT, due_at TEXT, implemented_at TEXT,
    verification_status TEXT
);
CREATE TABLE pcb.effectiveness_check (
    check_id TEXT PRIMARY KEY, corrective_action_id TEXT, metric TEXT, baseline_value DOUBLE PRECISION,
    target_value DOUBLE PRECISION, post_value DOUBLE PRECISION, window_from TEXT, window_to TEXT,
    result TEXT, checked_at TEXT
);
CREATE TABLE pcb.recurrence_review (
    review_id TEXT PRIMARY KEY, rma_item_id TEXT, corrective_action_id TEXT, lot_produced_after_action BIGINT,
    symptom_match BIGINT, cause_match BIGINT, verdict TEXT, reviewed_at TEXT
);

INSERT INTO pcb.investigation VALUES ('INV-A', '공정 조건 추적'), ('INV-B', '다른 조사');
INSERT INTO pcb.rma_claim VALUES
    ('RMA-BEFORE', '2025-12-09T23:59:59+09:00'),
    ('RMA-AFTER', '2025-12-09T15:00:00Z'),
    ('RMA-OUTSIDE', '2025-12-17T01:00:00+09:00');
INSERT INTO pcb.rma_item VALUES
    ('I-BEFORE', 'RMA-BEFORE'), ('I-AFTER-1', 'RMA-AFTER'), ('I-AFTER-2', 'RMA-AFTER'), ('I-OUTSIDE', 'RMA-OUTSIDE');
INSERT INTO pcb.investigation_claim VALUES
    ('INV-A', 'RMA-BEFORE', '2025-12-10T01:00:00+09:00'),
    ('INV-A', 'RMA-BEFORE', '2025-12-11T01:00:00+09:00'),
    ('INV-A', 'RMA-AFTER', '2025-12-11T02:00:00+09:00'),
    ('INV-B', 'RMA-OUTSIDE', '2025-12-18T10:00:00+09:00');
INSERT INTO pcb.corrective_action VALUES
    ('CA-01', 'INV-A', 'OCCURRENCE', 'PROCESS_CONTROL', '공정 조건 변경', 'QA', 'C-1',
        '2025-12-07T11:00:00+09:00', '2025-12-11T11:00:00+09:00', '2025-12-09T15:00:00Z', 'MONITORING'),
    ('CA-02', 'INV-A', 'ESCAPE', 'INSPECTION', '검사 기준 변경', 'QA', NULL,
        '2025-12-11T10:00:00+09:00', '2026-01-11T11:00:00+09:00', NULL, 'IMPLEMENTING'),
    ('CA-03', 'INV-B', 'HORIZONTAL', 'EXTEND', '수평 전개', 'QA', NULL,
        '2025-12-22T11:00:00+09:00', '2026-01-11T11:00:00+09:00', NULL, 'IMPLEMENTING');
INSERT INTO pcb.effectiveness_check VALUES
    ('CHK-01', 'CA-01', '동일 원인 접수 수', 8, 0, 2,
        '2025-12-09T15:00:00Z', '2025-12-31T12:30:00+09:00', 'NOT_EFFECTIVE', '2025-12-31T12:40:00+09:00'),
    ('CHK-02', 'CA-01', '적용 Lot 비율', NULL, 90, NULL,
        '2025-12-11T00:00:00+09:00', '2026-01-11T00:00:00+09:00', 'MONITORING', NULL);

-- 중복 품목과 정정 전 재발 이력은 최신 상태의 접수 건수를 늘리지 않음
INSERT INTO pcb.recurrence_review VALUES
    ('REV-01', 'I-BEFORE', 'CA-01', 1, 1, 1, 'RECURRENCE', '2025-12-11T10:00:00+09:00'),
    ('REV-02', 'I-BEFORE', 'CA-01', 0, 1, 1, 'PRE_ACTION_PRODUCT', '2025-12-11T11:00:00+09:00'),
    ('REV-03', 'I-AFTER-1', 'CA-01', 1, 1, 1, 'RECURRENCE', '2025-12-12T11:00:00+09:00'),
    ('REV-04', 'I-AFTER-2', 'CA-01', 1, 1, 1, 'RECURRENCE', '2025-12-12T12:00:00+09:00'),
    ('REV-05', 'I-OUTSIDE', 'CA-01', 1, 1, 1, 'RECURRENCE', '2025-12-19T10:00:00+09:00'),
    ('REV-06', 'I-OUTSIDE', 'CA-03', 1, 1, 0, 'PENDING_CAUSE_REVIEW', '2025-12-31T13:00:00+09:00');
