package com.u1_2.effect.rma.domain.communication;

import com.u1_2.effect.rma.domain.analysis.AnalysisResultStates;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RmaCommunicationRepository {

    // 요청 키 확인과 AI 재분류 조회는 같은 고객 보충 회신만 대상으로 삼음
    private static final String CUSTOMER_SUPPLEMENTS = """
            SELECT c.message_text FROM app.communication_meta meta
            JOIN pcb.communication c ON c.message_id = meta.message_id
            WHERE meta.rma_id = :rmaId AND meta.source = 'CUSTOMER_SUPPLEMENT'
            """;

    private final JdbcClient jdbcClient;

    public List<Message> findMessages(String rmaId, boolean includeInternal) {
        // CS에는 고객에게 보낸 회신과 고객이 보낸 메시지만 보여 줌
        return jdbcClient.sql("""
                SELECT c.message_id, c.audience, c.author_role,
                       CASE WHEN c.author_role <> 'customer' THEN u.name END AS author_name,
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
                VALUES (:messageId, :rmaId, :customerId, :audience, :authorRole, :messageText, :sentAt)
                """).paramSource(message).update();
        jdbcClient.sql("""
                INSERT INTO app.communication_meta (message_id, rma_id, source, analysis_id, author_user_id, created_at, request_key)
                VALUES (:messageId, :rmaId, :source, :analysisId, :authorUserId,
                        COALESCE(:createdAt, clock_timestamp()), :requestKey)
                """).paramSource(message).update();
    }

    public Optional<String> findCustomerSupplement(String rmaId, long authorUserId, String key) {
        return jdbcClient.sql(CUSTOMER_SUPPLEMENTS + """
                AND meta.author_user_id = :authorUserId AND meta.request_key = :key
                """).param("rmaId", rmaId).param("authorUserId", authorUserId).param("key", key)
                .query(String.class).optional();
    }

    public List<String> findCustomerSupplements(String rmaId) {
        // 고객에게 보낸 답변은 분류에 넣지 않고 현재 접수에 받은 보충 회신만 순서대로 넘김
        return jdbcClient.sql(CUSTOMER_SUPPLEMENTS + """
                ORDER BY meta.created_at, meta.message_id
                """).param("rmaId", rmaId).query(String.class).list();
    }

    public Optional<FollowUpContext> findFollowUpContext(String rmaId) {
        // 승인 또는 확인을 마친 같은 접수의 분석만 연결하고 공개 가능한 품번만 가져옴
        return jdbcClient.sql("""
                SELECT c.reported_part_no, d.analysis_id
                FROM pcb.rma_claim c
                JOIN app.cause_decision d ON d.rma_id = c.rma_id
                JOIN app.ai_analysis a ON a.analysis_id = d.analysis_id AND a.rma_id = c.rma_id
                WHERE c.rma_id = :rmaId AND c.qa_status = 'CAUSE_DECIDED'
                    AND d.approval_status IN ('APPROVED', 'CONFIRMED') AND d.decided_at IS NOT NULL
                    AND a.analysis_state IN (:resultStates)
                ORDER BY d.decided_at DESC, d.id DESC LIMIT 1
                """).param("rmaId", rmaId).param("resultStates", AnalysisResultStates.WITH_RESULT)
                .query(FollowUpContext.class).optional();
    }

    public record FollowUpContext(String reportedPartNo, String analysisId) {
    }

    public record Message(String messageId, String audience, String authorRole, String authorName, String messageText,
                          OffsetDateTime sentAt) {
    }

    public record NewMessage(String messageId, String rmaId, String customerId, String audience, String authorRole,
                             String messageText, String sentAt, String source, String analysisId, long authorUserId,
                             OffsetDateTime createdAt, String requestKey) {
    }
}
