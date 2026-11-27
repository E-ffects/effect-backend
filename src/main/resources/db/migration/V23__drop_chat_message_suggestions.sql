-- 답변 아래 다음 행동 버튼(suggestions)은 받아 줄 화면이 없어 계약에서 뺌
ALTER TABLE app.chat_message DROP COLUMN suggestions_json;
