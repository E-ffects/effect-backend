package com.u1_2.effect.rma.domain.communication;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RmaCommunicationRepository {

    private final JdbcClient jdbcClient;

    public List<Message> findMessages(String rmaId, boolean includeInternal) {
        // CS에는 고객에게 보낸 회신과 고객이 보낸 메시지만 보여 줌
        return jdbcClient.sql("""
                SELECT c.message_id, c.audience, c.author_role, u.name AS author_name,
                       c.message_text, CAST(c.sent_at AS timestamptz) AS sent_at
                FROM pcb.communication c
                LEFT JOIN app.communication_meta meta ON meta.message_id = c.message_id
                LEFT JOIN users u ON u.id = meta.author_user_id
                WHERE c.rma_id = :rmaId AND (:includeInternal OR c.audience = 'customer' OR c.author_role = 'customer')
                ORDER BY sent_at, c.message_id
                """).param("rmaId", rmaId).param("includeInternal", includeInternal).query(Message.class).list();
    }

    public void insertMessage(NewMessage message) {
        jdbcClient.sql("""
                INSERT INTO pcb.communication (message_id, rma_id, customer_id, audience, author_role, message_text, sent_at)
                VALUES (:messageId, :rmaId, :customerId, 'customer', 'CS', :messageText, :sentAt)
                """).paramSource(message).update();
        jdbcClient.sql("""
                INSERT INTO app.communication_meta (message_id, rma_id, source, analysis_id, author_user_id, created_at)
                VALUES (:messageId, :rmaId, :source, :analysisId, :authorUserId, :createdAt)
                """).paramSource(message).update();
    }

    public Optional<String> findItemId(String rmaId) {
        return jdbcClient.sql("""
                SELECT rma_item_id FROM pcb.rma_item WHERE rma_id = :rmaId ORDER BY rma_item_id LIMIT 1
                """).param("rmaId", rmaId).query(String.class).optional();
    }

    public void insertServiceAction(ServiceAction action) {
        jdbcClient.sql("""
                INSERT INTO pcb.service_action (action_id, rma_item_id, action_type, policy_version,
                    decided_at, executed_at, board_qty, result)
                VALUES (:actionId, :itemId, :actionType, 'B2B-QA-2025', :decidedAt, :executedAt, :boardQty, :result)
                """).paramSource(action).update();
    }

    public record Message(String messageId, String audience, String authorRole, String authorName, String messageText,
                          OffsetDateTime sentAt) {
    }

    public record NewMessage(String messageId, String rmaId, String customerId, String messageText, String sentAt,
                             String source, String analysisId, long authorUserId, OffsetDateTime createdAt) {
    }

    public record ServiceAction(String actionId, String itemId, String actionType, long boardQty,
                                String result, String decidedAt, String executedAt) {
    }
}
