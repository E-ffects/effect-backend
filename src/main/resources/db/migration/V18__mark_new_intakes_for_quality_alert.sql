-- 기존 접수는 보존하고 이 마이그레이션 이후 등록되는 접수만 QA 알림 대상으로 표시함
ALTER TABLE app.intake ADD COLUMN notify_quality_on_intake BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE app.intake ALTER COLUMN notify_quality_on_intake SET DEFAULT TRUE;
