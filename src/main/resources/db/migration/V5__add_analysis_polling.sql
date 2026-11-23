ALTER TABLE app.ai_analysis
    ADD COLUMN updated_at TIMESTAMPTZ,
    ADD COLUMN poll_started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN poll_interval_ms INTEGER NOT NULL DEFAULT 1000 CHECK (poll_interval_ms > 0),
    ADD COLUMN next_poll_at TIMESTAMPTZ NOT NULL DEFAULT (CURRENT_TIMESTAMP + INTERVAL '1 second');

UPDATE app.ai_analysis SET updated_at = COALESCE(completed_at, created_at);

CREATE INDEX ix_ai_analysis_pending_poll ON app.ai_analysis (next_poll_at, analysis_id)
    WHERE analysis_state IN ('QUEUED', 'RUNNING');
