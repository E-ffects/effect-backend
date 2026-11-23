CREATE SCHEMA IF NOT EXISTS pcb;

CREATE TABLE pcb.rma_claim (
    rma_id TEXT PRIMARY KEY,
    opened_at TEXT NOT NULL,
    cs_status TEXT NOT NULL
);

CREATE TABLE pcb.rma_item (
    rma_item_id TEXT PRIMARY KEY,
    rma_id TEXT NOT NULL REFERENCES pcb.rma_claim
);

CREATE TABLE pcb.service_action (
    action_id TEXT PRIMARY KEY,
    rma_item_id TEXT NOT NULL REFERENCES pcb.rma_item,
    executed_at TEXT
);

CREATE TABLE pcb.loss_ledger (
    ledger_id TEXT PRIMARY KEY,
    occurred_at TEXT NOT NULL,
    amount_krw BIGINT NOT NULL
);

-- 기간보다 오래된 접수도 현재 미종료면 처리 중 건수에 포함함
INSERT INTO pcb.rma_claim VALUES
    ('RMA-A', '2025-12-01T23:59:59+09:00', 'OPEN'),
    ('RMA-B', '2025-12-30T15:00:00Z', 'RESPONDED'),
    ('RMA-C', '2025-12-01T11:00:00+09:00', 'CLOSED'),
    ('RMA-D', '2025-12-31T23:59:59+09:00', 'CLOSED'),
    ('RMA-E', '2025-12-10T10:00:00+09:00', 'OPEN');

INSERT INTO pcb.rma_item VALUES
    ('ITEM-C1', 'RMA-C'), ('ITEM-C2', 'RMA-C'), ('ITEM-D1', 'RMA-D'), ('ITEM-E1', 'RMA-E');

-- RMA-D의 첫 실행은 12월 31일이지만 마지막 실행은 1월 1일이므로 그날을 종료일로 봄
INSERT INTO pcb.service_action VALUES
    ('ACTION-C1', 'ITEM-C1', '2025-12-31T15:00:00+09:00'),
    ('ACTION-C2', 'ITEM-C2', '2025-12-31T23:59:59+09:00'),
    ('ACTION-D1', 'ITEM-D1', '2025-12-31T17:00:00+09:00'),
    ('ACTION-D2', 'ITEM-D1', '2025-12-31T15:00:00Z'),
    ('ACTION-E1', 'ITEM-E1', NULL);

INSERT INTO pcb.loss_ledger VALUES
    ('LOSS-1', '2025-12-30T15:00:00Z', 1000),
    ('LOSS-2', '2025-12-31T23:59:59+09:00', 500),
    ('LOSS-3', '2025-12-31T15:00:00Z', 750);
