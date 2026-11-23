ALTER TABLE app.intake
    ADD COLUMN classification_json JSONB,
    ADD COLUMN triage_result_json JSONB,
    ADD COLUMN error_detail TEXT;
