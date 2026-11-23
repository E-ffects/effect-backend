CREATE TABLE pcb.rma_claim (
    rma_id TEXT PRIMARY KEY,
    customer_id TEXT NOT NULL REFERENCES pcb.customer (customer_id),
    opened_at TEXT NOT NULL,
    occurred_at TEXT NOT NULL,
    raw_text TEXT NOT NULL,
    reported_part_no TEXT NOT NULL,
    reported_lot_no TEXT,
    reported_date_code TEXT,
    detection_stage TEXT NOT NULL,
    cs_status TEXT NOT NULL,
    qa_status TEXT NOT NULL
);

CREATE TABLE pcb.rma_item (
    rma_item_id TEXT PRIMARY KEY,
    rma_id TEXT NOT NULL REFERENCES pcb.rma_claim (rma_id),
    panel_id TEXT,
    lot_id TEXT,
    input_qty BIGINT NOT NULL,
    claimed_defect_qty BIGINT NOT NULL,
    returned_qty BIGINT NOT NULL,
    verified_defect_qty BIGINT
);

CREATE TABLE pcb.symptom_assessment (
    assessment_id TEXT PRIMARY KEY,
    rma_item_id TEXT NOT NULL REFERENCES pcb.rma_item (rma_item_id),
    primary_label TEXT NOT NULL REFERENCES pcb.symptom_definition (minor_code),
    status TEXT NOT NULL,
    evidence_spans_json TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE TABLE pcb.qa_triage (
    triage_id TEXT PRIMARY KEY,
    rma_id TEXT NOT NULL REFERENCES pcb.rma_claim (rma_id),
    severity BIGINT NOT NULL,
    characteristic_symbol TEXT,
    analysis_level TEXT NOT NULL,
    rationale TEXT NOT NULL,
    decided_by TEXT NOT NULL,
    decided_at TEXT NOT NULL
);

CREATE TABLE pcb.recurrence_review (
    review_id TEXT PRIMARY KEY,
    rma_item_id TEXT NOT NULL REFERENCES pcb.rma_item (rma_item_id),
    verdict TEXT NOT NULL,
    reviewed_at TEXT NOT NULL
);

INSERT INTO pcb.rma_claim VALUES
    ('RMA-08927', 'A01', '2025-11-20T08:56:35+09:00', '2025-11-19T06:31:47+09:00',
     '숏트 불량이 발생했습니다', 'PCB-ECU6-A1', 'LT-01603', '2543', 'ICT_FUNCTION', 'OPEN', 'ANALYZING'),
    ('RMA-10611', 'A01', '2025-12-31T09:00:00+09:00', '2025-12-29T00:00:00+09:00',
     '신규 접수 원문', 'PCB-ECU6-A1', NULL, NULL, 'CUSTOMER_IQC', 'OPEN', 'RECEIVED'),
    ('RMA-00015', 'A02', '2025-01-06T08:46:55+09:00', '2025-01-05T15:53:27+09:00',
     '기존 접수 원문', 'PCB-MEM10-D1', NULL, NULL, 'FIELD', 'CLOSED', 'CLOSED');

INSERT INTO pcb.rma_item VALUES
    ('ITEM-08927', 'RMA-08927', 'PANEL-01', 'LT-01603', 2050, 109, 2, NULL),
    ('ITEM-10611', 'RMA-10611', NULL, NULL, 650, 3, 0, NULL),
    ('ITEM-00015', 'RMA-00015', NULL, NULL, 500, 2, 2, 0);

INSERT INTO pcb.recurrence_review VALUES
    ('REVIEW-00015', 'ITEM-00015', 'RECURRENCE', '2025-01-07T09:00:00+09:00');

INSERT INTO pcb.symptom_assessment VALUES
    ('SYM-08927', 'ITEM-08927', 'ELEC02', 'PROPOSED', '["숏트 불량이 발생했습니다"]', '2025-11-20T12:00:00+09:00');

INSERT INTO pcb.qa_triage VALUES
    ('TRI-08927', 'RMA-08927', 8, NULL, 'L3', '차량 고객의 심각도 8 불량', 'QA_AGENT', '2025-11-20T12:41:37+09:00');

INSERT INTO app.intake (rma_id, intake_state, created_by, created_at, updated_at) VALUES
    ('RMA-10611', 'NEEDS_INFO', 1, '2025-12-31T09:00:00+09:00', '2025-12-31T09:00:01+09:00');

-- 고정 시계에서는 생성 시각이 같아도 실제 시작 시각이 늦은 분석을 선택해야 함
INSERT INTO app.ai_analysis (analysis_id, rma_id, mode, analysis_state, created_at, poll_started_at) VALUES
    ('ana-older', 'RMA-08927', 'B', 'FAILED', '2025-11-20T12:45:00+09:00', '2025-11-20T12:45:00+09:00'),
    ('ana-latest', 'RMA-08927', 'B', 'SUCCEEDED', '2025-11-20T12:45:00+09:00', '2025-11-20T12:46:00+09:00');

INSERT INTO app.cause_decision (rma_id, analysis_id, approval_status, analysis_level) VALUES
    ('RMA-08927', 'ana-latest', 'WAITING', 'L3');
