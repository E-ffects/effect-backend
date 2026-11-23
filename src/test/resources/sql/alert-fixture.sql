CREATE SCHEMA IF NOT EXISTS pcb;

CREATE TABLE pcb.rma_claim (rma_id TEXT PRIMARY KEY, reported_part_no TEXT NOT NULL);
CREATE TABLE pcb.qa_triage (triage_id TEXT PRIMARY KEY, rma_id TEXT, severity INTEGER, decided_at TEXT);
CREATE TABLE pcb.rma_item (rma_item_id TEXT PRIMARY KEY, rma_id TEXT);
CREATE TABLE pcb.recurrence_review (
    review_id TEXT PRIMARY KEY, rma_item_id TEXT, corrective_action_id TEXT, verdict TEXT, reviewed_at TEXT
);

INSERT INTO users (id, username, password, name, role, created_at, updated_at)
VALUES (91001, 'alerts-a', 'unused', '품질 A', 'QUALITY', LOCALTIMESTAMP, LOCALTIMESTAMP),
       (91002, 'alerts-b', 'unused', '품질 B', 'QUALITY', LOCALTIMESTAMP, LOCALTIMESTAMP);
INSERT INTO pcb.rma_claim VALUES ('RMA-A', 'PCB-A'), ('RMA-B', 'PCB-B');
INSERT INTO pcb.qa_triage VALUES
    ('QA-01', 'RMA-A', 9, '2025-12-17T09:05:00+09:00'),
    ('QA-02', 'RMA-A', 9, '2025-12-17T10:00:00+09:00'),
    ('QA-03', 'RMA-B', 8, '2025-12-17T10:00:00+09:00');
INSERT INTO pcb.rma_item VALUES ('ITEM-B1', 'RMA-B'), ('ITEM-B2', 'RMA-B');
INSERT INTO pcb.recurrence_review VALUES
    ('REV-01', 'ITEM-B1', 'CA-02', 'PENDING_CAUSE_REVIEW', '2025-12-19T01:00:00Z'),
    ('REV-02', 'ITEM-B2', 'CA-02', 'PENDING_CAUSE_REVIEW', '2025-12-19T11:00:00+09:00');
