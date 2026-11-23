ALTER TABLE app.cause_decision ADD COLUMN next_refresh_at TIMESTAMPTZ;

UPDATE app.cause_decision SET next_refresh_at = CURRENT_TIMESTAMP WHERE refresh_status = 'PENDING';

CREATE INDEX ix_cause_decision_refresh_due ON app.cause_decision (next_refresh_at, id)
    WHERE refresh_status = 'PENDING';
