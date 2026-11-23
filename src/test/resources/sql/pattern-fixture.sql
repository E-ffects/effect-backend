ALTER TABLE pcb.rma_item ADD COLUMN lot_id TEXT;
ALTER TABLE pcb.claim_cause ADD COLUMN claim_cause_id TEXT;
ALTER TABLE pcb.corrective_action
    ADD COLUMN description TEXT,
    ADD COLUMN verification_status TEXT,
    ADD COLUMN selected_at TEXT;

CREATE TABLE pcb.process_run (lot_id TEXT, equipment_id TEXT, ended_at TEXT);

-- 대표 접수의 제품이 달라도 제품 필터는 조사 안의 각 접수에 적용함
UPDATE pcb.rma_claim SET reported_part_no = 'PCB-B' WHERE rma_id = 'RMA-B';
UPDATE pcb.rma_item SET lot_id = 'LOT-A' WHERE rma_item_id IN ('A1', 'A2');
UPDATE pcb.rma_item SET lot_id = 'LOT-B' WHERE rma_item_id = 'B1';
UPDATE pcb.claim_cause SET claim_cause_id = rma_item_id;
UPDATE pcb.corrective_action
SET description = '공정 조건 확인', verification_status = 'EFFECTIVE', selected_at = '2025-12-02T10:00:00+09:00';

INSERT INTO pcb.process_run VALUES
    ('LOT-A', 'EQ-1', '2025-12-01T10:00:00+09:00'),
    ('LOT-A', 'EQ-1', '2025-12-01T12:00:00+09:00'),
    ('LOT-B', 'EQ-1', '2025-12-30T10:00:00+09:00'),
    ('LOT-B', 'EQ-FUTURE', '2026-01-01T00:00:00+09:00');

INSERT INTO pcb.investigation VALUES ('INV-EMPTY', '연결 대기', 'L1', 'ANALYSIS');
