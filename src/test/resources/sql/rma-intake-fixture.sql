CREATE TABLE pcb.production_lot (
    lot_id TEXT PRIMARY KEY,
    spec_id TEXT NOT NULL REFERENCES pcb.product_spec (spec_id)
);

INSERT INTO users (id, username, password, name, role, created_at, updated_at)
VALUES (7, 'quality-reviewer', 'unused', '품질 담당자', 'QUALITY', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

ALTER TABLE pcb.symptom_definition ADD COLUMN label_version TEXT NOT NULL DEFAULT 'pcb-v3';

CREATE TABLE pcb.rma_claim (
    rma_id TEXT PRIMARY KEY,
    external_claim_id TEXT NOT NULL,
    customer_id TEXT NOT NULL REFERENCES pcb.customer (customer_id),
    opened_at TEXT NOT NULL,
    occurred_at TEXT NOT NULL,
    raw_text TEXT NOT NULL,
    reported_part_no TEXT NOT NULL,
    reported_lot_no TEXT,
    reported_date_code TEXT,
    detection_stage TEXT NOT NULL,
    cs_status TEXT NOT NULL,
    qa_status TEXT NOT NULL,
    idempotency_key TEXT NOT NULL
);

CREATE TABLE pcb.communication (
    message_id TEXT PRIMARY KEY,
    rma_id TEXT REFERENCES pcb.rma_claim (rma_id),
    investigation_id TEXT,
    customer_id TEXT REFERENCES pcb.customer (customer_id),
    audience TEXT NOT NULL,
    author_role TEXT NOT NULL,
    message_text TEXT NOT NULL,
    sent_at TEXT NOT NULL
);

CREATE TABLE pcb.rma_item (
    rma_item_id TEXT PRIMARY KEY,
    rma_id TEXT NOT NULL REFERENCES pcb.rma_claim (rma_id),
    panel_id TEXT,
    lot_id TEXT REFERENCES pcb.production_lot (lot_id),
    shipment_lot_id TEXT,
    input_qty BIGINT NOT NULL,
    claimed_defect_qty BIGINT NOT NULL,
    returned_qty BIGINT NOT NULL,
    verified_defect_qty BIGINT,
    trace_status TEXT NOT NULL
);

CREATE TABLE pcb.symptom_assessment (
    assessment_id TEXT PRIMARY KEY,
    rma_item_id TEXT NOT NULL REFERENCES pcb.rma_item (rma_item_id),
    primary_label TEXT NOT NULL REFERENCES pcb.symptom_definition (minor_code),
    label_version TEXT NOT NULL,
    secondary_labels_json TEXT NOT NULL,
    evidence_spans_json TEXT NOT NULL,
    proposed_by TEXT NOT NULL,
    confirmed_by TEXT,
    status TEXT NOT NULL,
    created_at TEXT NOT NULL,
    intake_label TEXT
);

CREATE TABLE pcb.qa_triage (
    triage_id TEXT PRIMARY KEY,
    rma_id TEXT NOT NULL REFERENCES pcb.rma_claim (rma_id),
    severity BIGINT NOT NULL,
    characteristic_id TEXT,
    characteristic_symbol TEXT,
    customer_industry TEXT NOT NULL,
    repeat_signal BIGINT NOT NULL,
    analysis_level TEXT NOT NULL,
    rationale TEXT NOT NULL,
    decided_by TEXT NOT NULL,
    decided_at TEXT NOT NULL
);

INSERT INTO pcb.product_spec VALUES ('SPEC-002', 'PCB-MEM10-D1', '메모리');
INSERT INTO pcb.production_lot VALUES ('LT-01603', 'SPEC-001'), ('LT-02000', 'SPEC-002');

INSERT INTO pcb.rma_claim VALUES
    ('RMA-10610', 'EXT-10610', 'A01', '2025-11-20T08:56:35+09:00', '2025-11-19T06:31:47+09:00',
     'v5 적재 건', 'PCB-ECU6-A1', NULL, NULL, 'ICT_FUNCTION', 'CLOSED', 'CLOSED', 'IMPORT-RMA-10610');

INSERT INTO pcb.rma_item VALUES
    ('ITEM-10610', 'RMA-10610', NULL, NULL, NULL, 100, 10, 10, 1, 'DATE_CODE_TRACED');
