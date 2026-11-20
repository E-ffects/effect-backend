CREATE SCHEMA pcb;

CREATE TABLE pcb.customer (
    customer_id TEXT PRIMARY KEY,
    customer_name TEXT NOT NULL,
    industry TEXT NOT NULL
);

CREATE TABLE pcb.product_spec (
    spec_id TEXT PRIMARY KEY,
    part_no TEXT NOT NULL,
    product_family TEXT NOT NULL
);

CREATE TABLE pcb.symptom_definition (
    minor_code TEXT PRIMARY KEY,
    major_code TEXT NOT NULL,
    major_name TEXT NOT NULL,
    minor_name TEXT NOT NULL
);

INSERT INTO pcb.customer VALUES
    ('A02', '고객사 A02', 'AUTOMOTIVE'),
    ('A01', '고객사 A01', 'AUTOMOTIVE');

INSERT INTO pcb.product_spec VALUES ('SPEC-001', 'PCB-ECU6-A1', '차량 ECU');

INSERT INTO pcb.symptom_definition VALUES ('ELEC02', 'ELEC', '전기적 불량', '쇼트');
