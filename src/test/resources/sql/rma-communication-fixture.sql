CREATE TABLE pcb.service_action (
    action_id TEXT PRIMARY KEY,
    rma_item_id TEXT NOT NULL REFERENCES pcb.rma_item (rma_item_id),
    action_type TEXT NOT NULL,
    policy_version TEXT NOT NULL,
    decided_at TEXT NOT NULL,
    executed_at TEXT,
    board_qty BIGINT NOT NULL,
    result TEXT NOT NULL
);

INSERT INTO users (id, username, password, name, role, created_at, updated_at)
VALUES (8, 'cs-writer', 'unused', '고객 대응 담당자', 'CS', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
