CREATE TABLE app.communication_meta (
    message_id      TEXT PRIMARY KEY,
    rma_id          TEXT NOT NULL,
    source          TEXT NOT NULL,
    analysis_id     TEXT REFERENCES app.ai_analysis (analysis_id),
    author_user_id  BIGINT NOT NULL REFERENCES users (id),
    created_at      TIMESTAMPTZ NOT NULL
);
