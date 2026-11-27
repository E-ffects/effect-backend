-- 특정 RMA·패턴에 묶이지 않은 화면(대시보드, 목록 등)의 대화. scope_id는 고정값 '-'라 사용자당 열린 대화가 하나임
ALTER TABLE app.chat_thread DROP CONSTRAINT chat_thread_scope_type_check;
ALTER TABLE app.chat_thread ADD CONSTRAINT chat_thread_scope_type_check
    CHECK (scope_type IN ('RMA', 'PATTERN', 'GENERAL'));
