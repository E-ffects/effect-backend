package com.u1_2.effect.chat.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ChatRepository {

    private final JdbcClient jdbcClient;

    public Optional<ChatThread> findThread(UUID threadId) {
        return jdbcClient.sql("""
                SELECT thread_id, user_id, scope_type, scope_id, closed_at
                FROM app.chat_thread WHERE thread_id = :threadId
                """).param("threadId", threadId).query(ChatThread.class).optional();
    }

    public Optional<ChatThread> findOpenThread(long userId, ChatScopeType type, String scopeId) {
        return jdbcClient.sql("""
                SELECT thread_id, user_id, scope_type, scope_id, closed_at
                FROM app.chat_thread
                WHERE user_id = :userId AND scope_type = :type AND scope_id = :scopeId AND closed_at IS NULL
                """).param("userId", userId).param("type", type.name()).param("scopeId", scopeId)
                .query(ChatThread.class).optional();
    }

    public void closeOpenThreads(long userId, ChatScopeType type, String scopeId, OffsetDateTime now) {
        jdbcClient.sql("""
                UPDATE app.chat_thread SET closed_at = :now
                WHERE user_id = :userId AND scope_type = :type AND scope_id = :scopeId AND closed_at IS NULL
                """).param("now", now).param("userId", userId).param("type", type.name()).param("scopeId", scopeId)
                .update();
    }

    /** 같은 범위의 열린 대화를 다른 요청이 먼저 만들었으면 false. 유일 인덱스가 인스턴스 사이 경합을 막음 */
    public boolean insertThread(UUID threadId, long userId, ChatScopeType type, String scopeId, OffsetDateTime now) {
        return jdbcClient.sql("""
                INSERT INTO app.chat_thread (thread_id, user_id, scope_type, scope_id, created_at, last_message_at)
                VALUES (:threadId, :userId, :type, :scopeId, :now, :now)
                ON CONFLICT (user_id, scope_type, scope_id) WHERE closed_at IS NULL DO NOTHING
                """).param("threadId", threadId).param("userId", userId).param("type", type.name())
                .param("scopeId", scopeId).param("now", now).update() == 1;
    }

    public void touchThread(UUID threadId, OffsetDateTime now) {
        jdbcClient.sql("UPDATE app.chat_thread SET last_message_at = :now WHERE thread_id = :threadId")
                .param("now", now).param("threadId", threadId).update();
    }

    public void insertMessage(UUID threadId, NewMessage message) {
        jdbcClient.sql("""
                INSERT INTO app.chat_message
                    (thread_id, role, text, status, evidence_json, suggestions_json, model_version, created_at)
                VALUES (:threadId, :role, :text, :status, CAST(:evidence AS jsonb), CAST(:suggestions AS jsonb),
                        :modelVersion, :createdAt)
                """).param("threadId", threadId).param("role", message.role()).param("text", message.text())
                .param("status", message.status()).param("evidence", message.evidenceJson())
                .param("suggestions", message.suggestionsJson()).param("modelVersion", message.modelVersion())
                .param("createdAt", message.createdAt()).update();
    }

    /** 최근 limit개를 오래된 순서로 반환함 */
    public List<Message> findRecentMessages(UUID threadId, int limit) {
        return jdbcClient.sql("""
                SELECT * FROM (
                    SELECT message_id, role, text, status, evidence_json::text AS evidence_json,
                           suggestions_json::text AS suggestions_json, created_at
                    FROM app.chat_message WHERE thread_id = :threadId
                    ORDER BY message_id DESC LIMIT :limit
                ) recent ORDER BY message_id
                """).param("threadId", threadId).param("limit", limit).query(Message.class).list();
    }

    /** 마지막 메시지가 기준 시각보다 오래된 대화를 지움. 여러 인스턴스가 함께 실행해도 결과가 같음 */
    public int deleteThreadsBefore(OffsetDateTime cutoff) {
        return jdbcClient.sql("DELETE FROM app.chat_thread WHERE last_message_at < :cutoff")
                .param("cutoff", cutoff).update();
    }

    public record ChatThread(UUID threadId, long userId, String scopeType, String scopeId, OffsetDateTime closedAt) {
        public boolean belongsTo(long user, ChatScopeType type, String id) {
            return userId == user && scopeType.equals(type.name()) && scopeId.equals(id) && closedAt == null;
        }
    }

    public record NewMessage(String role, String text, String status, String evidenceJson, String suggestionsJson,
                             String modelVersion, OffsetDateTime createdAt) {
    }

    public record Message(long messageId, String role, String text, String status, String evidenceJson,
                          String suggestionsJson, OffsetDateTime createdAt) {
    }
}
