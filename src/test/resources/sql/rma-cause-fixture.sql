CREATE TABLE pcb.process_master (process_id TEXT PRIMARY KEY);
CREATE TABLE pcb.equipment_master (equipment_id TEXT PRIMARY KEY);

CREATE TABLE pcb.cause_master (
  cause_code TEXT PRIMARY KEY NOT NULL,
  cause_category TEXT NOT NULL,
  process_id TEXT REFERENCES pcb.process_master(process_id) DEFERRABLE INITIALLY DEFERRED,
  cause_name TEXT NOT NULL,
  mechanism TEXT NOT NULL,
  required_evidence TEXT NOT NULL,
  standard_action TEXT NOT NULL,
  action_owner_dept TEXT NOT NULL
);

CREATE TABLE pcb.investigation (
  investigation_id TEXT PRIMARY KEY NOT NULL,
  title TEXT NOT NULL,
  analysis_level TEXT NOT NULL,
  scope_definition TEXT NOT NULL,
  owner TEXT NOT NULL,
  opened_at TEXT NOT NULL,
  status TEXT NOT NULL,
  closed_at TEXT,
  reopened_from TEXT REFERENCES pcb.investigation(investigation_id) DEFERRABLE INITIALLY DEFERRED
);
CREATE TABLE pcb.cause_assessment (
  cause_assessment_id TEXT PRIMARY KEY NOT NULL,
  investigation_id TEXT NOT NULL REFERENCES pcb.investigation(investigation_id) DEFERRABLE INITIALLY DEFERRED,
  cause_code TEXT NOT NULL REFERENCES pcb.cause_master(cause_code) DEFERRABLE INITIALLY DEFERRED,
  cause_category TEXT NOT NULL,
  process_id TEXT REFERENCES pcb.process_master(process_id) DEFERRABLE INITIALLY DEFERRED,
  equipment_id TEXT REFERENCES pcb.equipment_master(equipment_id) DEFERRABLE INITIALLY DEFERRED,
  material_lot_ids_json TEXT NOT NULL,
  certainty_status TEXT NOT NULL,
  support_evidence_ids_json TEXT NOT NULL,
  assessed_at TEXT NOT NULL,
  approved_at TEXT
);
CREATE TABLE pcb.investigation_claim (
  link_id TEXT PRIMARY KEY NOT NULL,
  investigation_id TEXT NOT NULL REFERENCES pcb.investigation(investigation_id) DEFERRABLE INITIALLY DEFERRED,
  rma_id TEXT NOT NULL REFERENCES pcb.rma_claim(rma_id) DEFERRABLE INITIALLY DEFERRED,
  membership_reason TEXT NOT NULL,
  linked_at TEXT NOT NULL
);
CREATE INDEX ix_investigation_claim_investigation_id ON pcb.investigation_claim(investigation_id);
CREATE TABLE pcb.claim_cause (
  claim_cause_id TEXT PRIMARY KEY NOT NULL,
  rma_item_id TEXT NOT NULL REFERENCES pcb.rma_item(rma_item_id) DEFERRABLE INITIALLY DEFERRED,
  cause_assessment_id TEXT NOT NULL REFERENCES pcb.cause_assessment(cause_assessment_id) DEFERRABLE INITIALLY DEFERRED,
  applicability TEXT NOT NULL,
  rationale TEXT NOT NULL,
  decided_at TEXT
);
CREATE INDEX ix_claim_cause_rma_item_id ON pcb.claim_cause(rma_item_id);

INSERT INTO pcb.process_master VALUES ('OL_ETCH');
INSERT INTO pcb.equipment_master VALUES ('OLE-02');
INSERT INTO pcb.cause_master VALUES
    ('OL_ETCH_RESIDUE', 'PROCESS', 'OL_ETCH', '외층 에칭 잔동', '기전', '근거', '대책', '품질');
