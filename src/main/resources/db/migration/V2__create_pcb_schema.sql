-- PCB RMA v5 스키마 (팀 공식 데이터, effect-ai datagen/schema.sql 기준)
-- 테이블 46개, 외래 키 75개, 인덱스 15개, 뷰 10개. 이름과 타입은 SQLite 배포본과 같다.
-- 데이터 적재는 Flyway에 넣지 않는다: effect-ai db/postgres/build.py로 만든 load.sql의 \copy 부분만 실행한다.
-- AI 서버는 이 스키마를 읽기만 한다(RMA_DB_SCHEMA=pcb).

CREATE SCHEMA IF NOT EXISTS pcb;
SET LOCAL search_path TO pcb;

CREATE TABLE "analysis_level_rule" (
  "rule_id" text NOT NULL,
  "analysis_level" text NOT NULL,
  "condition_text" text NOT NULL,
  "required_outputs" text NOT NULL,
  "report_type" text NOT NULL,
  PRIMARY KEY ("rule_id")
);

CREATE TABLE "cause_assessment" (
  "cause_assessment_id" text NOT NULL,
  "investigation_id" text NOT NULL,
  "cause_code" text NOT NULL,
  "cause_category" text NOT NULL,
  "process_id" text,
  "equipment_id" text,
  "material_lot_ids_json" text NOT NULL,
  "certainty_status" text NOT NULL,
  "support_evidence_ids_json" text NOT NULL,
  "assessed_at" text NOT NULL,
  "approved_at" text,
  PRIMARY KEY ("cause_assessment_id")
);

CREATE TABLE "cause_candidate" (
  "candidate_id" text NOT NULL,
  "investigation_id" text NOT NULL,
  "candidate_no" bigint NOT NULL,
  "cause_code" text,
  "review_note" text NOT NULL,
  "commonality_dimension" text NOT NULL,
  "review_method" text NOT NULL,
  "review_status" text NOT NULL,
  "review_evidence_ids_json" text NOT NULL,
  "reviewed_at" text,
  PRIMARY KEY ("candidate_id")
);

CREATE TABLE "cause_master" (
  "cause_code" text NOT NULL,
  "cause_category" text NOT NULL,
  "process_id" text,
  "cause_name" text NOT NULL,
  "mechanism" text NOT NULL,
  "required_evidence" text NOT NULL,
  "standard_action" text NOT NULL,
  "action_owner_dept" text NOT NULL,
  PRIMARY KEY ("cause_code")
);

CREATE TABLE "claim_cause" (
  "claim_cause_id" text NOT NULL,
  "rma_item_id" text NOT NULL,
  "cause_assessment_id" text NOT NULL,
  "applicability" text NOT NULL,
  "rationale" text NOT NULL,
  "decided_at" text,
  PRIMARY KEY ("claim_cause_id")
);

CREATE TABLE "claim_text_variant" (
  "rma_id" text NOT NULL,
  "variant" text NOT NULL,
  "original_raw_text" text NOT NULL,
  "original_evidence_spans_json" text NOT NULL,
  PRIMARY KEY ("rma_id")
);

CREATE TABLE "communication" (
  "message_id" text NOT NULL,
  "rma_id" text,
  "investigation_id" text,
  "customer_id" text,
  "audience" text NOT NULL,
  "author_role" text NOT NULL,
  "message_text" text NOT NULL,
  "sent_at" text NOT NULL,
  PRIMARY KEY ("message_id")
);

CREATE TABLE "corrective_action" (
  "corrective_action_id" text NOT NULL,
  "investigation_id" text NOT NULL,
  "action_role" text NOT NULL,
  "action_category" text NOT NULL,
  "description" text NOT NULL,
  "owner_dept" text NOT NULL,
  "cause_code" text,
  "escape_code" text,
  "scope_json" text NOT NULL,
  "selected_at" text NOT NULL,
  "due_at" text NOT NULL,
  "implemented_at" text,
  "verification_status" text NOT NULL,
  PRIMARY KEY ("corrective_action_id")
);

CREATE TABLE "cost_rule" (
  "rule_id" text NOT NULL,
  "rule_group" text NOT NULL,
  "value" double precision NOT NULL,
  "unit" text NOT NULL,
  "basis" text NOT NULL,
  "is_assumption" bigint NOT NULL,
  PRIMARY KEY ("rule_id")
);

CREATE TABLE "customer" (
  "customer_id" text NOT NULL,
  "customer_name" text NOT NULL,
  "industry" text NOT NULL,
  "region" text NOT NULL,
  "quality_standard" text NOT NULL,
  "ppap_required" bigint NOT NULL,
  PRIMARY KEY ("customer_id")
);

CREATE TABLE "customer_test_report" (
  "report_id" text NOT NULL,
  "rma_item_id" text NOT NULL,
  "test_type" text NOT NULL,
  "reported_value" double precision,
  "unit" text,
  "conditions_json" text NOT NULL,
  "fixture_info_provided" bigint NOT NULL,
  "origin_verified" bigint NOT NULL,
  "received_at" text NOT NULL,
  PRIMARY KEY ("report_id")
);

CREATE TABLE "effectiveness_check" (
  "check_id" text NOT NULL,
  "corrective_action_id" text NOT NULL,
  "metric" text NOT NULL,
  "baseline_value" double precision,
  "target_value" double precision,
  "post_value" double precision,
  "window_from" text NOT NULL,
  "window_to" text NOT NULL,
  "result" text NOT NULL,
  "checked_at" text,
  PRIMARY KEY ("check_id")
);

CREATE TABLE "equipment_event" (
  "event_id" text NOT NULL,
  "equipment_id" text NOT NULL,
  "event_type" text NOT NULL,
  "occurred_at" text NOT NULL,
  "recorded_at" text NOT NULL,
  "metric_name" text,
  "metric_value" double precision,
  "metric_unit" text,
  "description" text NOT NULL,
  "result" text NOT NULL,
  PRIMARY KEY ("event_id")
);

CREATE TABLE "equipment_master" (
  "equipment_id" text NOT NULL,
  "process_id" text NOT NULL,
  "equipment_type" text NOT NULL,
  "line" text NOT NULL,
  "installed_at" text NOT NULL,
  PRIMARY KEY ("equipment_id")
);

CREATE TABLE "escape_assessment" (
  "escape_id" text NOT NULL,
  "investigation_id" text NOT NULL,
  "escape_code" text NOT NULL,
  "detection_process_id" text,
  "test_run_id" text,
  "evidence_ids_json" text NOT NULL,
  "status" text NOT NULL,
  "assessed_at" text NOT NULL,
  PRIMARY KEY ("escape_id")
);

CREATE TABLE "escape_master" (
  "escape_code" text NOT NULL,
  "escape_name" text NOT NULL,
  "detection_process_id" text,
  "description" text NOT NULL,
  "standard_action" text NOT NULL,
  PRIMARY KEY ("escape_code")
);

CREATE TABLE "evidence" (
  "evidence_id" text NOT NULL,
  "investigation_id" text,
  "source_type" text NOT NULL,
  "source_record_id" text NOT NULL,
  "occurred_at" text NOT NULL,
  "available_at" text NOT NULL,
  "author" text NOT NULL,
  "content_summary" text NOT NULL,
  "origin_verified" bigint NOT NULL,
  "is_synthetic" bigint NOT NULL,
  PRIMARY KEY ("evidence_id")
);

CREATE TABLE "inspection_plan" (
  "plan_id" text NOT NULL,
  "plan_code" text NOT NULL,
  "phase" text NOT NULL,
  "test_spec_ids_json" text NOT NULL,
  "applies_to" text NOT NULL,
  "sample_rule" text NOT NULL,
  "sample_rate" double precision NOT NULL,
  "revision" bigint NOT NULL,
  "effective_from" text NOT NULL,
  "effective_to" text,
  "change_reason" text NOT NULL,
  "corrective_action_id" text,
  "approval_status" text NOT NULL,
  PRIMARY KEY ("plan_id")
);

CREATE TABLE "investigation" (
  "investigation_id" text NOT NULL,
  "title" text NOT NULL,
  "analysis_level" text NOT NULL,
  "scope_definition" text NOT NULL,
  "owner" text NOT NULL,
  "opened_at" text NOT NULL,
  "status" text NOT NULL,
  "closed_at" text,
  "reopened_from" text,
  PRIMARY KEY ("investigation_id")
);

CREATE TABLE "investigation_claim" (
  "link_id" text NOT NULL,
  "investigation_id" text NOT NULL,
  "rma_id" text NOT NULL,
  "membership_reason" text NOT NULL,
  "linked_at" text NOT NULL,
  PRIMARY KEY ("link_id")
);

CREATE TABLE "loss_ledger" (
  "ledger_id" text NOT NULL,
  "cost_category" text NOT NULL,
  "cost_type" text NOT NULL,
  "rma_id" text,
  "rma_item_id" text,
  "investigation_id" text,
  "customer_id" text,
  "spec_id" text,
  "quantity" double precision NOT NULL,
  "unit" text NOT NULL,
  "unit_cost_krw" bigint NOT NULL,
  "amount_krw" bigint NOT NULL,
  "basis" text NOT NULL,
  "source_table" text NOT NULL,
  "source_id" text NOT NULL,
  "occurred_at" text NOT NULL,
  PRIMARY KEY ("ledger_id")
);

CREATE TABLE "material_lot" (
  "material_lot_id" text NOT NULL,
  "material_type" text NOT NULL,
  "supplier_id" text NOT NULL,
  "grade" text NOT NULL,
  "received_at" text NOT NULL,
  "qty" double precision NOT NULL,
  "unit" text NOT NULL,
  "coa_json" text NOT NULL,
  "iqc_method" text NOT NULL,
  "iqc_result" text NOT NULL,
  "iqc_at" text NOT NULL,
  "usage_status" text NOT NULL,
  PRIMARY KEY ("material_lot_id")
);

CREATE TABLE "material_usage" (
  "usage_id" text NOT NULL,
  "lot_id" text NOT NULL,
  "material_lot_id" text NOT NULL,
  "material_type" text NOT NULL,
  "issued_at" text NOT NULL,
  PRIMARY KEY ("usage_id")
);

CREATE TABLE "process_master" (
  "process_id" text NOT NULL,
  "process_name" text NOT NULL,
  "stage_group" text NOT NULL,
  "stage_seq" bigint NOT NULL,
  "sequence_no" bigint NOT NULL,
  "batch_unit" text NOT NULL,
  "is_inspection" bigint NOT NULL,
  PRIMARY KEY ("process_id")
);

CREATE TABLE "process_observation" (
  "observation_id" text NOT NULL,
  "process_run_id" text NOT NULL,
  "item_code" text NOT NULL,
  "value" double precision NOT NULL,
  "unit" text NOT NULL,
  "target" double precision NOT NULL,
  "lower_limit" double precision NOT NULL,
  "upper_limit" double precision NOT NULL,
  "measured_at" text NOT NULL,
  "spc_flag" text NOT NULL,
  PRIMARY KEY ("observation_id")
);

CREATE TABLE "process_parameter_spec" (
  "param_id" text NOT NULL,
  "process_id" text NOT NULL,
  "item_code" text NOT NULL,
  "unit" text NOT NULL,
  "target" double precision NOT NULL,
  "lower_limit" double precision NOT NULL,
  "upper_limit" double precision NOT NULL,
  "applies_to" text NOT NULL,
  "characteristic_id" text,
  PRIMARY KEY ("param_id")
);

CREATE TABLE "process_run" (
  "process_run_id" text NOT NULL,
  "lot_id" text NOT NULL,
  "process_id" text NOT NULL,
  "equipment_id" text NOT NULL,
  "load_no" bigint NOT NULL,
  "panel_seq_from" bigint NOT NULL,
  "panel_seq_to" bigint NOT NULL,
  "recipe_version" text NOT NULL,
  "started_at" text NOT NULL,
  "ended_at" text NOT NULL,
  "shift" text NOT NULL,
  "result" text NOT NULL,
  PRIMARY KEY ("process_run_id")
);

CREATE TABLE "product_cost" (
  "spec_id" text NOT NULL,
  "panel_cost_krw" bigint NOT NULL,
  "board_cost_krw" bigint NOT NULL,
  "board_price_krw" bigint NOT NULL,
  "basis" text NOT NULL,
  PRIMARY KEY ("spec_id")
);

CREATE TABLE "product_spec" (
  "spec_id" text NOT NULL,
  "part_no" text NOT NULL,
  "revision" text NOT NULL,
  "product_family" text NOT NULL,
  "application" text NOT NULL,
  "layer_count" bigint NOT NULL,
  "board_thickness_mm" double precision NOT NULL,
  "surface_finish" text NOT NULL,
  "base_material" text NOT NULL,
  "min_line_space_um" bigint NOT NULL,
  "impedance_control" bigint NOT NULL,
  "hdi" bigint NOT NULL,
  "boards_per_panel" bigint NOT NULL,
  "effective_from" text NOT NULL,
  PRIMARY KEY ("spec_id")
);

CREATE TABLE "production_lot" (
  "lot_id" text NOT NULL,
  "spec_id" text NOT NULL,
  "work_order_id" text NOT NULL,
  "date_code" text NOT NULL,
  "started_at" text NOT NULL,
  "completed_at" text NOT NULL,
  "panel_qty" bigint NOT NULL,
  "status" text NOT NULL,
  PRIMARY KEY ("lot_id")
);

CREATE TABLE "qa_causal_requirement" (
  "cause_code" text NOT NULL,
  "requirement_text" text NOT NULL,
  "min_verified_lab_evidence" bigint NOT NULL,
  "requires_production_record" bigint NOT NULL,
  "requires_comparison_sample" bigint NOT NULL,
  PRIMARY KEY ("cause_code")
);

CREATE TABLE "qa_triage" (
  "triage_id" text NOT NULL,
  "rma_id" text NOT NULL,
  "severity" bigint NOT NULL,
  "characteristic_id" text,
  "characteristic_symbol" text,
  "customer_industry" text NOT NULL,
  "repeat_signal" bigint NOT NULL,
  "analysis_level" text NOT NULL,
  "rationale" text NOT NULL,
  "decided_by" text NOT NULL,
  "decided_at" text NOT NULL,
  PRIMARY KEY ("triage_id")
);

CREATE TABLE "recurrence_review" (
  "review_id" text NOT NULL,
  "rma_item_id" text NOT NULL,
  "corrective_action_id" text NOT NULL,
  "lot_produced_after_action" bigint NOT NULL,
  "symptom_match" bigint NOT NULL,
  "cause_match" bigint NOT NULL,
  "verdict" text NOT NULL,
  "reviewed_at" text NOT NULL,
  PRIMARY KEY ("review_id")
);

CREATE TABLE "rma_claim" (
  "rma_id" text NOT NULL,
  "external_claim_id" text NOT NULL,
  "customer_id" text NOT NULL,
  "opened_at" text NOT NULL,
  "occurred_at" text NOT NULL,
  "raw_text" text NOT NULL,
  "reported_part_no" text NOT NULL,
  "reported_lot_no" text,
  "reported_date_code" text,
  "detection_stage" text NOT NULL,
  "cs_status" text NOT NULL,
  "qa_status" text NOT NULL,
  "idempotency_key" text NOT NULL,
  PRIMARY KEY ("rma_id")
);

CREATE TABLE "rma_item" (
  "rma_item_id" text NOT NULL,
  "rma_id" text NOT NULL,
  "panel_id" text,
  "lot_id" text,
  "shipment_lot_id" text,
  "input_qty" bigint NOT NULL,
  "claimed_defect_qty" bigint NOT NULL,
  "returned_qty" bigint NOT NULL,
  "verified_defect_qty" bigint,
  "trace_status" text NOT NULL,
  PRIMARY KEY ("rma_item_id")
);

CREATE TABLE "service_action" (
  "action_id" text NOT NULL,
  "rma_item_id" text NOT NULL,
  "action_type" text NOT NULL,
  "policy_version" text NOT NULL,
  "decided_at" text NOT NULL,
  "executed_at" text,
  "board_qty" bigint NOT NULL,
  "result" text NOT NULL,
  PRIMARY KEY ("action_id")
);

CREATE TABLE "shipment" (
  "shipment_id" text NOT NULL,
  "customer_id" text NOT NULL,
  "order_item_id" text NOT NULL,
  "shipped_at" text NOT NULL,
  "delivered_at" text NOT NULL,
  "board_qty" bigint NOT NULL,
  "status" text NOT NULL,
  PRIMARY KEY ("shipment_id")
);

CREATE TABLE "shipment_lot" (
  "shipment_lot_id" text NOT NULL,
  "shipment_id" text NOT NULL,
  "lot_id" text NOT NULL,
  "panel_seq_from" bigint NOT NULL,
  "panel_seq_to" bigint NOT NULL,
  "panel_qty" bigint NOT NULL,
  "board_qty" bigint NOT NULL,
  PRIMARY KEY ("shipment_lot_id")
);

CREATE TABLE "similar_cases" (
  "case_id" text NOT NULL,
  "query_rma_id" text NOT NULL,
  "reference_rma_id" text NOT NULL,
  "searched_at" text NOT NULL,
  "similarity_score" double precision NOT NULL,
  "similarity_basis" text NOT NULL,
  "applicability" text NOT NULL,
  "search_version" text NOT NULL,
  PRIMARY KEY ("case_id")
);

CREATE TABLE "special_characteristic" (
  "characteristic_id" text NOT NULL,
  "characteristic_name" text NOT NULL,
  "symbol" text NOT NULL,
  "control_method" text NOT NULL,
  "related_process_id" text,
  "related_test_spec_id" text,
  PRIMARY KEY ("characteristic_id")
);

CREATE TABLE "supplier" (
  "supplier_id" text NOT NULL,
  "supplier_name" text NOT NULL,
  "material_type" text NOT NULL,
  "approval_status" text NOT NULL,
  PRIMARY KEY ("supplier_id")
);

CREATE TABLE "symptom_assessment" (
  "assessment_id" text NOT NULL,
  "rma_item_id" text NOT NULL,
  "primary_label" text NOT NULL,
  "label_version" text NOT NULL,
  "secondary_labels_json" text NOT NULL,
  "evidence_spans_json" text NOT NULL,
  "proposed_by" text NOT NULL,
  "confirmed_by" text,
  "status" text NOT NULL,
  "created_at" text NOT NULL,
  "intake_label" text,
  "reclassified_at" text,
  PRIMARY KEY ("assessment_id")
);

CREATE TABLE "symptom_definition" (
  "minor_code" text NOT NULL,
  "major_code" text NOT NULL,
  "major_name" text NOT NULL,
  "minor_name" text NOT NULL,
  "label_version" text NOT NULL,
  "severity" bigint NOT NULL,
  "characteristic_id" text,
  "inclusion_rules" text NOT NULL,
  "exclusion_rules" text NOT NULL,
  PRIMARY KEY ("minor_code")
);

CREATE TABLE "test_measurement" (
  "measurement_id" text NOT NULL,
  "test_run_id" text NOT NULL,
  "test_spec_id" text NOT NULL,
  "trial_index" bigint NOT NULL,
  "raw_value" double precision,
  "unit" text NOT NULL,
  "condition_json" text NOT NULL,
  "result" text NOT NULL,
  PRIMARY KEY ("measurement_id")
);

CREATE TABLE "test_run" (
  "test_run_id" text NOT NULL,
  "phase" text NOT NULL,
  "subject_type" text NOT NULL,
  "subject_id" text NOT NULL,
  "lot_id" text,
  "rma_item_id" text,
  "investigation_id" text,
  "process_run_id" text,
  "equipment_id" text,
  "plan_id" text,
  "sample_origin" text NOT NULL,
  "origin_verified" bigint NOT NULL,
  "started_at" text NOT NULL,
  "ended_at" text NOT NULL,
  "overall_result" text NOT NULL,
  PRIMARY KEY ("test_run_id")
);

CREATE TABLE "test_spec" (
  "test_spec_id" text NOT NULL,
  "item_code" text NOT NULL,
  "test_name" text NOT NULL,
  "phase_group" text NOT NULL,
  "method_version" text NOT NULL,
  "unit" text NOT NULL,
  "lower_limit" double precision,
  "upper_limit" double precision,
  "characteristic_id" text,
  "valid_from" text NOT NULL,
  PRIMARY KEY ("test_spec_id")
);

ALTER TABLE "cause_assessment" ADD CONSTRAINT "fk_cause_assessment_0" FOREIGN KEY ("equipment_id") REFERENCES "equipment_master" ("equipment_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "cause_assessment" ADD CONSTRAINT "fk_cause_assessment_1" FOREIGN KEY ("process_id") REFERENCES "process_master" ("process_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "cause_assessment" ADD CONSTRAINT "fk_cause_assessment_2" FOREIGN KEY ("cause_code") REFERENCES "cause_master" ("cause_code") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "cause_assessment" ADD CONSTRAINT "fk_cause_assessment_3" FOREIGN KEY ("investigation_id") REFERENCES "investigation" ("investigation_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "cause_candidate" ADD CONSTRAINT "fk_cause_candidate_0" FOREIGN KEY ("cause_code") REFERENCES "cause_master" ("cause_code") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "cause_candidate" ADD CONSTRAINT "fk_cause_candidate_1" FOREIGN KEY ("investigation_id") REFERENCES "investigation" ("investigation_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "cause_master" ADD CONSTRAINT "fk_cause_master_0" FOREIGN KEY ("process_id") REFERENCES "process_master" ("process_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "claim_cause" ADD CONSTRAINT "fk_claim_cause_0" FOREIGN KEY ("cause_assessment_id") REFERENCES "cause_assessment" ("cause_assessment_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "claim_cause" ADD CONSTRAINT "fk_claim_cause_1" FOREIGN KEY ("rma_item_id") REFERENCES "rma_item" ("rma_item_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "claim_text_variant" ADD CONSTRAINT "fk_claim_text_variant_0" FOREIGN KEY ("rma_id") REFERENCES "rma_claim" ("rma_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "communication" ADD CONSTRAINT "fk_communication_0" FOREIGN KEY ("customer_id") REFERENCES "customer" ("customer_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "communication" ADD CONSTRAINT "fk_communication_1" FOREIGN KEY ("investigation_id") REFERENCES "investigation" ("investigation_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "communication" ADD CONSTRAINT "fk_communication_2" FOREIGN KEY ("rma_id") REFERENCES "rma_claim" ("rma_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "corrective_action" ADD CONSTRAINT "fk_corrective_action_0" FOREIGN KEY ("escape_code") REFERENCES "escape_master" ("escape_code") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "corrective_action" ADD CONSTRAINT "fk_corrective_action_1" FOREIGN KEY ("cause_code") REFERENCES "cause_master" ("cause_code") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "corrective_action" ADD CONSTRAINT "fk_corrective_action_2" FOREIGN KEY ("investigation_id") REFERENCES "investigation" ("investigation_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "customer_test_report" ADD CONSTRAINT "fk_customer_test_report_0" FOREIGN KEY ("rma_item_id") REFERENCES "rma_item" ("rma_item_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "effectiveness_check" ADD CONSTRAINT "fk_effectiveness_check_0" FOREIGN KEY ("corrective_action_id") REFERENCES "corrective_action" ("corrective_action_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "equipment_event" ADD CONSTRAINT "fk_equipment_event_0" FOREIGN KEY ("equipment_id") REFERENCES "equipment_master" ("equipment_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "equipment_master" ADD CONSTRAINT "fk_equipment_master_0" FOREIGN KEY ("process_id") REFERENCES "process_master" ("process_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "escape_assessment" ADD CONSTRAINT "fk_escape_assessment_0" FOREIGN KEY ("test_run_id") REFERENCES "test_run" ("test_run_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "escape_assessment" ADD CONSTRAINT "fk_escape_assessment_1" FOREIGN KEY ("detection_process_id") REFERENCES "process_master" ("process_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "escape_assessment" ADD CONSTRAINT "fk_escape_assessment_2" FOREIGN KEY ("escape_code") REFERENCES "escape_master" ("escape_code") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "escape_assessment" ADD CONSTRAINT "fk_escape_assessment_3" FOREIGN KEY ("investigation_id") REFERENCES "investigation" ("investigation_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "escape_master" ADD CONSTRAINT "fk_escape_master_0" FOREIGN KEY ("detection_process_id") REFERENCES "process_master" ("process_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "evidence" ADD CONSTRAINT "fk_evidence_0" FOREIGN KEY ("investigation_id") REFERENCES "investigation" ("investigation_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "inspection_plan" ADD CONSTRAINT "fk_inspection_plan_0" FOREIGN KEY ("corrective_action_id") REFERENCES "corrective_action" ("corrective_action_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "investigation" ADD CONSTRAINT "fk_investigation_0" FOREIGN KEY ("reopened_from") REFERENCES "investigation" ("investigation_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "investigation_claim" ADD CONSTRAINT "fk_investigation_claim_0" FOREIGN KEY ("rma_id") REFERENCES "rma_claim" ("rma_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "investigation_claim" ADD CONSTRAINT "fk_investigation_claim_1" FOREIGN KEY ("investigation_id") REFERENCES "investigation" ("investigation_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "loss_ledger" ADD CONSTRAINT "fk_loss_ledger_0" FOREIGN KEY ("spec_id") REFERENCES "product_spec" ("spec_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "loss_ledger" ADD CONSTRAINT "fk_loss_ledger_1" FOREIGN KEY ("customer_id") REFERENCES "customer" ("customer_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "loss_ledger" ADD CONSTRAINT "fk_loss_ledger_2" FOREIGN KEY ("investigation_id") REFERENCES "investigation" ("investigation_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "loss_ledger" ADD CONSTRAINT "fk_loss_ledger_3" FOREIGN KEY ("rma_item_id") REFERENCES "rma_item" ("rma_item_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "loss_ledger" ADD CONSTRAINT "fk_loss_ledger_4" FOREIGN KEY ("rma_id") REFERENCES "rma_claim" ("rma_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "material_lot" ADD CONSTRAINT "fk_material_lot_0" FOREIGN KEY ("supplier_id") REFERENCES "supplier" ("supplier_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "material_usage" ADD CONSTRAINT "fk_material_usage_0" FOREIGN KEY ("material_lot_id") REFERENCES "material_lot" ("material_lot_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "material_usage" ADD CONSTRAINT "fk_material_usage_1" FOREIGN KEY ("lot_id") REFERENCES "production_lot" ("lot_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "process_observation" ADD CONSTRAINT "fk_process_observation_0" FOREIGN KEY ("process_run_id") REFERENCES "process_run" ("process_run_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "process_parameter_spec" ADD CONSTRAINT "fk_process_parameter_spec_0" FOREIGN KEY ("characteristic_id") REFERENCES "special_characteristic" ("characteristic_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "process_parameter_spec" ADD CONSTRAINT "fk_process_parameter_spec_1" FOREIGN KEY ("process_id") REFERENCES "process_master" ("process_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "process_run" ADD CONSTRAINT "fk_process_run_0" FOREIGN KEY ("equipment_id") REFERENCES "equipment_master" ("equipment_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "process_run" ADD CONSTRAINT "fk_process_run_1" FOREIGN KEY ("process_id") REFERENCES "process_master" ("process_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "process_run" ADD CONSTRAINT "fk_process_run_2" FOREIGN KEY ("lot_id") REFERENCES "production_lot" ("lot_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "product_cost" ADD CONSTRAINT "fk_product_cost_0" FOREIGN KEY ("spec_id") REFERENCES "product_spec" ("spec_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "production_lot" ADD CONSTRAINT "fk_production_lot_0" FOREIGN KEY ("spec_id") REFERENCES "product_spec" ("spec_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "qa_causal_requirement" ADD CONSTRAINT "fk_qa_causal_requirement_0" FOREIGN KEY ("cause_code") REFERENCES "cause_master" ("cause_code") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "qa_triage" ADD CONSTRAINT "fk_qa_triage_0" FOREIGN KEY ("analysis_level") REFERENCES "analysis_level_rule" ("rule_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "qa_triage" ADD CONSTRAINT "fk_qa_triage_1" FOREIGN KEY ("characteristic_id") REFERENCES "special_characteristic" ("characteristic_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "qa_triage" ADD CONSTRAINT "fk_qa_triage_2" FOREIGN KEY ("rma_id") REFERENCES "rma_claim" ("rma_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "recurrence_review" ADD CONSTRAINT "fk_recurrence_review_0" FOREIGN KEY ("corrective_action_id") REFERENCES "corrective_action" ("corrective_action_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "recurrence_review" ADD CONSTRAINT "fk_recurrence_review_1" FOREIGN KEY ("rma_item_id") REFERENCES "rma_item" ("rma_item_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "rma_claim" ADD CONSTRAINT "fk_rma_claim_0" FOREIGN KEY ("customer_id") REFERENCES "customer" ("customer_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "rma_item" ADD CONSTRAINT "fk_rma_item_0" FOREIGN KEY ("shipment_lot_id") REFERENCES "shipment_lot" ("shipment_lot_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "rma_item" ADD CONSTRAINT "fk_rma_item_1" FOREIGN KEY ("lot_id") REFERENCES "production_lot" ("lot_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "rma_item" ADD CONSTRAINT "fk_rma_item_2" FOREIGN KEY ("rma_id") REFERENCES "rma_claim" ("rma_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "service_action" ADD CONSTRAINT "fk_service_action_0" FOREIGN KEY ("rma_item_id") REFERENCES "rma_item" ("rma_item_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "shipment" ADD CONSTRAINT "fk_shipment_0" FOREIGN KEY ("customer_id") REFERENCES "customer" ("customer_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "shipment_lot" ADD CONSTRAINT "fk_shipment_lot_0" FOREIGN KEY ("lot_id") REFERENCES "production_lot" ("lot_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "shipment_lot" ADD CONSTRAINT "fk_shipment_lot_1" FOREIGN KEY ("shipment_id") REFERENCES "shipment" ("shipment_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "similar_cases" ADD CONSTRAINT "fk_similar_cases_0" FOREIGN KEY ("reference_rma_id") REFERENCES "rma_claim" ("rma_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "similar_cases" ADD CONSTRAINT "fk_similar_cases_1" FOREIGN KEY ("query_rma_id") REFERENCES "rma_claim" ("rma_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "special_characteristic" ADD CONSTRAINT "fk_special_characteristic_0" FOREIGN KEY ("related_process_id") REFERENCES "process_master" ("process_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "symptom_assessment" ADD CONSTRAINT "fk_symptom_assessment_0" FOREIGN KEY ("primary_label") REFERENCES "symptom_definition" ("minor_code") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "symptom_assessment" ADD CONSTRAINT "fk_symptom_assessment_1" FOREIGN KEY ("rma_item_id") REFERENCES "rma_item" ("rma_item_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "symptom_definition" ADD CONSTRAINT "fk_symptom_definition_0" FOREIGN KEY ("characteristic_id") REFERENCES "special_characteristic" ("characteristic_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "test_measurement" ADD CONSTRAINT "fk_test_measurement_0" FOREIGN KEY ("test_spec_id") REFERENCES "test_spec" ("test_spec_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "test_measurement" ADD CONSTRAINT "fk_test_measurement_1" FOREIGN KEY ("test_run_id") REFERENCES "test_run" ("test_run_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "test_run" ADD CONSTRAINT "fk_test_run_0" FOREIGN KEY ("plan_id") REFERENCES "inspection_plan" ("plan_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "test_run" ADD CONSTRAINT "fk_test_run_1" FOREIGN KEY ("equipment_id") REFERENCES "equipment_master" ("equipment_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "test_run" ADD CONSTRAINT "fk_test_run_2" FOREIGN KEY ("process_run_id") REFERENCES "process_run" ("process_run_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "test_run" ADD CONSTRAINT "fk_test_run_3" FOREIGN KEY ("investigation_id") REFERENCES "investigation" ("investigation_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "test_run" ADD CONSTRAINT "fk_test_run_4" FOREIGN KEY ("rma_item_id") REFERENCES "rma_item" ("rma_item_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "test_run" ADD CONSTRAINT "fk_test_run_5" FOREIGN KEY ("lot_id") REFERENCES "production_lot" ("lot_id") DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "test_spec" ADD CONSTRAINT "fk_test_spec_0" FOREIGN KEY ("characteristic_id") REFERENCES "special_characteristic" ("characteristic_id") DEFERRABLE INITIALLY DEFERRED;

CREATE INDEX ix_claim_cause_rma_item_id ON claim_cause(rma_item_id);
CREATE INDEX ix_evidence_investigation_id ON evidence(investigation_id);
CREATE INDEX ix_investigation_claim_investigation_id ON investigation_claim(investigation_id);
CREATE INDEX ix_loss_ledger_occurred on loss_ledger(occurred_at);
CREATE INDEX ix_loss_ledger_rma on loss_ledger(rma_id);
CREATE INDEX ix_material_usage_lot_id ON material_usage(lot_id);
CREATE INDEX ix_material_usage_material_lot_id ON material_usage(material_lot_id);
CREATE INDEX ix_process_observation_process_run_id ON process_observation(process_run_id);
CREATE INDEX ix_process_run_equipment_id ON process_run(equipment_id);
CREATE INDEX ix_process_run_lot_id ON process_run(lot_id);
CREATE INDEX ix_rma_item_lot_id ON rma_item(lot_id);
CREATE INDEX ix_shipment_lot_lot_id ON shipment_lot(lot_id);
CREATE INDEX ix_test_measurement_test_run_id ON test_measurement(test_run_id);
CREATE INDEX ix_test_run_lot_id ON test_run(lot_id);
CREATE INDEX ix_test_run_rma_item_id ON test_run(rma_item_id);

-- PCB RMA views for PostgreSQL. Same names, columns and results as the SQLite views (v4/v5).
-- Changes from SQLite: sum(<condition>) -> sum(CASE ...), json_each/json_extract -> jsonb functions.

CREATE VIEW monthly_claims AS
SELECT substr(c.opened_at, 1, 7) AS month, d.major_code, d.minor_code, d.minor_name, count(*) AS claims
FROM rma_claim c JOIN rma_item i USING (rma_id) JOIN symptom_assessment s USING (rma_item_id)
JOIN symptom_definition d ON d.minor_code = s.primary_label
GROUP BY 1, 2, 3, 4;

CREATE VIEW qa_case_overview AS
SELECT c.rma_id, c.opened_at, c.customer_id, cu.industry, c.reported_part_no, i.lot_id, i.panel_id, c.detection_stage,
       s.primary_label, t.severity, t.characteristic_symbol, t.analysis_level, ic.investigation_id,
       ca.cause_code, ca.cause_category, ca.certainty_status, cc.applicability, c.qa_status, c.cs_status
FROM rma_claim c JOIN customer cu USING (customer_id) JOIN rma_item i USING (rma_id)
JOIN symptom_assessment s USING (rma_item_id) JOIN qa_triage t USING (rma_id)
LEFT JOIN investigation_claim ic USING (rma_id) LEFT JOIN claim_cause cc ON cc.rma_item_id = i.rma_item_id
LEFT JOIN cause_assessment ca ON ca.cause_assessment_id = cc.cause_assessment_id;

CREATE VIEW spc_out_of_spec_by_equipment_month AS
SELECT r.equipment_id, r.process_id, o.item_code, substr(o.measured_at, 1, 7) AS month, count(*) AS observations,
       sum(CASE WHEN o.spc_flag = 'OUT_OF_SPEC' THEN 1 ELSE 0 END) AS out_of_spec
FROM process_observation o JOIN process_run r USING (process_run_id)
GROUP BY 1, 2, 3, 4;

CREATE VIEW special_characteristic_results AS
SELECT sc.characteristic_id, sc.characteristic_name, sc.symbol, tr.phase, substr(tr.started_at, 1, 7) AS month,
       count(*) AS measurements,
       sum(CASE WHEN m.result = 'FAIL' THEN 1 ELSE 0 END) AS fails,
       sum(CASE WHEN m.result = 'NOT_PERFORMED' THEN 1 ELSE 0 END) AS not_performed
FROM test_measurement m JOIN test_spec ts USING (test_spec_id)
JOIN special_characteristic sc ON sc.characteristic_id = ts.characteristic_id
JOIN test_run tr USING (test_run_id)
WHERE tr.phase IN ('FQC', 'OQC', 'IQC')
GROUP BY 1, 2, 3, 4, 5;

CREATE VIEW cause_confirmation_basis AS
SELECT c.cause_assessment_id, c.investigation_id, c.cause_code, c.cause_category, c.certainty_status,
       sum(CASE WHEN e.source_type = 'test_run' AND e.origin_verified = 1 THEN 1 ELSE 0 END) AS verified_lab_evidence,
       sum(CASE WHEN e.origin_verified = 0 THEN 1 ELSE 0 END) AS unverified_evidence,
       sum(CASE WHEN e.source_type IN ('process_run_summary', 'equipment_event', 'material_lot', 'inspection_plan')
                THEN 1 ELSE 0 END) AS production_record_evidence
FROM cause_assessment c
LEFT JOIN LATERAL jsonb_array_elements_text(coalesce(c.support_evidence_ids_json, '[]')::jsonb) AS j(value) ON true
LEFT JOIN evidence e ON e.evidence_id = j.value
GROUP BY 1, 2, 3, 4, 5;

CREATE VIEW escape_point_summary AS
SELECT e.escape_code, m.escape_name, m.detection_process_id, count(DISTINCT e.investigation_id) AS investigations,
       (SELECT count(*) FROM investigation_claim ic
        WHERE ic.investigation_id IN (SELECT investigation_id FROM escape_assessment x WHERE x.escape_code = e.escape_code)) AS claims
FROM escape_assessment e JOIN escape_master m USING (escape_code)
GROUP BY 1, 2, 3;

CREATE VIEW qa_symptom_reproduction AS
WITH mapping(minor_code, item_code) AS (
  VALUES ('ELEC03', 'LAB_IST_DR'), ('ELEC04', 'LAB_TDR_IMP'), ('ELEC05', 'LAB_SIR'), ('SOLD01', 'LAB_SOLDERABILITY'),
         ('STRC01', 'LAB_REFLOW_REPRO'), ('STRC02', 'LAB_XSEC_DELAM'), ('STRC03', 'LAB_WARPAGE'), ('STRC04', 'LAB_XSEC_CRACK'),
         ('APPR01', 'LAB_SM_TAPE'), ('DIM01', 'LAB_DIM'), ('MARK01', 'LAB_LABEL_MATCH')
), sample_test AS (
  SELECT * FROM test_run WHERE phase = 'RMA_LAB' AND subject_type = 'RETURNED_SAMPLE'
), matching AS (
  SELECT t.test_run_id, m.result
  FROM sample_test t JOIN symptom_assessment s USING (rma_item_id)
  JOIN mapping p ON p.minor_code = s.primary_label
  JOIN test_measurement m USING (test_run_id) JOIN test_spec ts USING (test_spec_id)
  WHERE ts.test_spec_id = p.item_code
    AND (s.primary_label <> 'STRC01' OR (m.condition_json::jsonb ->> 'condition') = 'as_received')
), summary AS (
  SELECT test_run_id,
         sum(CASE WHEN result = 'FAIL' THEN 1 ELSE 0 END) AS failures,
         sum(CASE WHEN result = 'PASS' THEN 1 ELSE 0 END) AS passes,
         sum(CASE WHEN result NOT IN ('PASS', 'FAIL') THEN 1 ELSE 0 END) AS incomplete
  FROM matching GROUP BY test_run_id
)
SELECT i.rma_id, i.rma_item_id, s.primary_label, t.test_run_id, t.origin_verified,
       t.overall_result, coalesce(a.failures, 0) AS matching_failures, coalesce(a.passes, 0) AS matching_passes,
       CASE WHEN t.test_run_id IS NULL THEN 'NOT_TESTED'
            WHEN t.origin_verified <> 1 THEN 'UNVERIFIED_SAMPLE'
            WHEN a.failures > 0 THEN 'REPRODUCED'
            WHEN a.passes > 0 AND a.incomplete = 0 THEN 'NOT_REPRODUCED'
            ELSE 'REVIEW_REQUIRED' END AS reproduction_status,
       cc.applicability, ca.cause_code, ca.certainty_status
FROM rma_item i JOIN symptom_assessment s USING (rma_item_id)
LEFT JOIN sample_test t USING (rma_item_id) LEFT JOIN summary a ON a.test_run_id = t.test_run_id
LEFT JOIN claim_cause cc USING (rma_item_id) LEFT JOIN cause_assessment ca USING (cause_assessment_id);

CREATE VIEW claim_loss AS
SELECT rma_id, sum(amount_krw) AS total_krw,
       sum(CASE WHEN cost_type = 'CREDIT_REFUND' THEN amount_krw ELSE 0 END) AS refund_krw,
       sum(CASE WHEN cost_type = 'REPLACEMENT_BOARDS' THEN amount_krw ELSE 0 END) AS replacement_krw,
       sum(CASE WHEN cost_type = 'CUSTOMER_CHARGEBACK' THEN amount_krw ELSE 0 END) AS chargeback_krw,
       sum(CASE WHEN cost_category = 'APPRAISAL' THEN amount_krw ELSE 0 END) AS appraisal_krw,
       sum(CASE WHEN cost_type IN ('LOGISTICS', 'SORTING_SUPPORT') THEN amount_krw ELSE 0 END) AS handling_krw
FROM loss_ledger WHERE rma_id IS NOT NULL GROUP BY rma_id;

CREATE VIEW loss_by_month AS
SELECT substr(occurred_at, 1, 7) AS month, cost_category, cost_type, count(*) AS entries, sum(amount_krw) AS amount_krw
FROM loss_ledger GROUP BY 1, 2, 3;

CREATE VIEW loss_by_investigation AS
SELECT l.investigation_id, i.title, i.analysis_level, sum(l.amount_krw) AS total_krw,
       sum(CASE WHEN l.cost_category = 'CONTAINMENT' THEN l.amount_krw ELSE 0 END) AS containment_krw,
       sum(CASE WHEN l.cost_type = 'CUSTOMER_CHARGEBACK' THEN l.amount_krw ELSE 0 END) AS chargeback_krw
FROM loss_ledger l JOIN investigation i USING (investigation_id) GROUP BY 1, 2, 3;
