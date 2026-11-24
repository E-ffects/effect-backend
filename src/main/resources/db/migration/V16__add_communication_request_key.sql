ALTER TABLE app.communication_meta ADD COLUMN request_key TEXT;

CREATE UNIQUE INDEX ux_communication_meta_request_key
    ON app.communication_meta (rma_id, author_user_id, request_key)
    WHERE request_key IS NOT NULL;

CREATE INDEX ix_communication_meta_customer_supplement
    ON app.communication_meta (rma_id, created_at, message_id)
    WHERE source = 'CUSTOMER_SUPPLEMENT';
