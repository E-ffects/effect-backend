-- 에이전트 패널 대화. AI 서버는 상태를 갖지 않고 Spring이 대화를 저장해 최근 메시지를 함께 보냄
CREATE TABLE app.chat_thread (
    thread_id       UUID PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    scope_type      TEXT        NOT NULL CHECK (scope_type IN ('RMA', 'PATTERN')),
    scope_id        TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    last_message_at TIMESTAMPTZ NOT NULL,
    closed_at       TIMESTAMPTZ
);

-- 사용자·범위마다 열린 대화는 하나. 여러 인스턴스가 동시에 새 대화를 열어도 하나만 남음
CREATE UNIQUE INDEX chat_thread_open_scope ON app.chat_thread (user_id, scope_type, scope_id)
    WHERE closed_at IS NULL;
CREATE INDEX chat_thread_last_message ON app.chat_thread (last_message_at);

CREATE TABLE app.chat_message (
    message_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    thread_id     UUID        NOT NULL REFERENCES app.chat_thread (thread_id) ON DELETE CASCADE,
    role          TEXT        NOT NULL CHECK (role IN ('user', 'assistant')),
    text          TEXT        NOT NULL,
    status        TEXT,
    evidence_json JSONB,
    suggestions_json JSONB,
    model_version TEXT,
    created_at    TIMESTAMPTZ NOT NULL
);

CREATE INDEX chat_message_thread ON app.chat_message (thread_id, message_id);
