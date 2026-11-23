ALTER TABLE pcb.corrective_action ADD COLUMN escape_code TEXT;
ALTER TABLE pcb.corrective_action ADD COLUMN scope_json TEXT NOT NULL DEFAULT '{}';
CREATE TABLE pcb.cause_master (cause_code TEXT PRIMARY KEY);
CREATE TABLE pcb.escape_master (escape_code TEXT PRIMARY KEY);
INSERT INTO pcb.cause_master VALUES ('C-1');
INSERT INTO pcb.escape_master VALUES ('E-1');
INSERT INTO users (id, username, password, name, role, created_at, updated_at)
VALUES (91007, 'corrective-action-qa', 'unused', '대책 담당자', 'QUALITY', LOCALTIMESTAMP, LOCALTIMESTAMP);
