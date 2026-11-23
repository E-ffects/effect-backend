CREATE TABLE pcb.communication (
    message_id TEXT PRIMARY KEY,
    rma_id TEXT REFERENCES pcb.rma_claim (rma_id),
    investigation_id TEXT REFERENCES pcb.investigation (investigation_id),
    customer_id TEXT REFERENCES pcb.customer (customer_id),
    audience TEXT NOT NULL,
    author_role TEXT NOT NULL,
    message_text TEXT NOT NULL,
    sent_at TEXT NOT NULL
);

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
