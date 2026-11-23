package com.u1_2.effect.recurrence.domain;

import com.u1_2.effect.global.dto.PageParameters;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RecurrenceRepository {

    // 화면 이력은 전체를 반환하지만 집계는 대책·품목의 최신 판정만 사용함
    private static final String LATEST_REVIEWS = """
            WITH latest_reviews AS (
                SELECT DISTINCT ON (r.corrective_action_id, r.rma_item_id)
                       r.corrective_action_id, r.rma_item_id, r.verdict
                FROM pcb.recurrence_review r
                WHERE r.reviewed_at::timestamptz < :reviewEnd
                  AND (CAST(:actionId AS text) IS NULL OR r.corrective_action_id = :actionId)
                ORDER BY r.corrective_action_id, r.rma_item_id, r.reviewed_at::timestamptz DESC, r.review_id DESC
            ), recurrences AS (
                SELECT DISTINCT r.corrective_action_id, item.rma_id
                FROM latest_reviews r JOIN pcb.rma_item item USING (rma_item_id)
                WHERE r.verdict = 'RECURRENCE'
            )
            """;

    private static final String ACTION_FILTER = """
            WHERE (CAST(:investigationId AS text) IS NULL OR a.investigation_id = :investigationId)
            """;

    private static final String REVIEW_FILTER = """
            WHERE (CAST(:actionId AS text) IS NULL OR r.corrective_action_id = :actionId)
              AND (CAST(:verdict AS text) IS NULL OR r.verdict = :verdict)
            """;

    private final JdbcClient jdbcClient;

    public List<ActionRow> findActions(String investigationId, PageParameters page, OffsetDateTime reviewEnd) {
        return page(actions(LATEST_REVIEWS + """
                , recurrence_counts AS (
                    SELECT corrective_action_id, count(*) AS recurrence_count
                    FROM recurrences GROUP BY corrective_action_id
                )
                SELECT a.corrective_action_id, a.investigation_id, i.title AS investigation_title,
                       a.action_role, a.action_category, a.description, a.owner_dept, a.cause_code,
                       a.selected_at::timestamptz AS selected_at, a.due_at::timestamptz AS due_at,
                       a.implemented_at::timestamptz AS implemented_at, a.verification_status,
                       coalesce(r.recurrence_count, 0) AS recurrence_count
                FROM pcb.corrective_action a
                JOIN pcb.investigation i USING (investigation_id)
                LEFT JOIN recurrence_counts r USING (corrective_action_id)
                """ + ACTION_FILTER + """
                ORDER BY (a.implemented_at IS NULL) DESC, a.implemented_at::timestamptz DESC,
                         a.selected_at::timestamptz DESC, a.corrective_action_id
                LIMIT :size OFFSET :offset
                """, investigationId).param("actionId", null).param("reviewEnd", reviewEnd), page)
                .query(ActionRow.class).list();
    }

    public long countActions(String investigationId) {
        return actions("SELECT count(*) FROM pcb.corrective_action a " + ACTION_FILTER, investigationId)
                .query(Long.class).single();
    }

    public Optional<ActionRef> findAction(String id) {
        return jdbcClient.sql("""
                SELECT corrective_action_id, investigation_id, implemented_at::timestamptz AS implemented_at
                FROM pcb.corrective_action WHERE corrective_action_id = :actionId
                """).param("actionId", id).query(ActionRef.class).optional();
    }

    public List<EffectCheck> findChecks(String actionId) {
        return jdbcClient.sql("""
                SELECT check_id, metric, baseline_value, target_value, post_value,
                       window_from::timestamptz AS window_from, window_to::timestamptz AS window_to, result,
                       checked_at::timestamptz AS checked_at
                FROM pcb.effectiveness_check
                WHERE corrective_action_id = :actionId
                ORDER BY window_from::timestamptz, window_to::timestamptz, check_id
                """).param("actionId", actionId).query(EffectCheck.class).list();
    }

    public List<Review> findReviews(String actionId, String verdict, PageParameters page) {
        return page(reviews("""
                SELECT r.review_id, r.corrective_action_id, item.rma_id, r.rma_item_id,
                       r.lot_produced_after_action <> 0 AS lot_produced_after_action,
                       r.symptom_match <> 0 AS symptom_match, r.cause_match <> 0 AS cause_match,
                       r.verdict, r.reviewed_at::timestamptz AS reviewed_at
                FROM pcb.recurrence_review r JOIN pcb.rma_item item USING (rma_item_id)
                """ + REVIEW_FILTER + """
                ORDER BY r.reviewed_at::timestamptz DESC, r.review_id DESC
                LIMIT :size OFFSET :offset
                """, actionId, verdict), page).query(Review.class).list();
    }

    public long countReviews(String actionId, String verdict) {
        return reviews("SELECT count(*) FROM pcb.recurrence_review r " + REVIEW_FILTER, actionId, verdict)
                .query(Long.class).single();
    }

    public List<DailyTrend> findDailyTrend(
            String actionId, String investigationId, OffsetDateTime start, OffsetDateTime end, OffsetDateTime reviewEnd
    ) {
        // 별도 조사로 넘어간 재발 접수도 대책의 판정과 연결돼 있으면 한 번 포함함
        return jdbcClient.sql(LATEST_REVIEWS + """
                , related_claims AS (
                    SELECT rma_id FROM pcb.investigation_claim
                    WHERE investigation_id = :investigationId AND linked_at::timestamptz < :reviewEnd
                    UNION
                    SELECT rma_id FROM recurrences
                )
                SELECT (c.opened_at::timestamptz AT TIME ZONE 'Asia/Seoul')::date AS day,
                       count(*) AS count, bool_or(r.rma_id IS NOT NULL) AS recurrence
                FROM related_claims x JOIN pcb.rma_claim c USING (rma_id)
                LEFT JOIN recurrences r USING (rma_id)
                WHERE c.opened_at::timestamptz >= :start AND c.opened_at::timestamptz < :end
                GROUP BY day ORDER BY day
                """).param("actionId", actionId).param("investigationId", investigationId)
                .param("start", start).param("end", end).param("reviewEnd", reviewEnd)
                .query(DailyTrend.class).list();
    }

    private JdbcClient.StatementSpec actions(String sql, String investigationId) {
        return jdbcClient.sql(sql).param("investigationId", investigationId);
    }

    private JdbcClient.StatementSpec reviews(String sql, String actionId, String verdict) {
        return jdbcClient.sql(sql).param("actionId", actionId).param("verdict", verdict);
    }

    private JdbcClient.StatementSpec page(JdbcClient.StatementSpec query, PageParameters page) {
        return query.param("size", page.size()).param("offset", page.offset());
    }

    public record ActionRef(
            String correctiveActionId, String investigationId, OffsetDateTime implementedAt
    ) {
    }

    public record ActionRow(
            String correctiveActionId, String investigationId, String investigationTitle, String actionRole,
            String actionCategory, String description, String ownerDept, String causeCode, OffsetDateTime selectedAt,
            OffsetDateTime dueAt, OffsetDateTime implementedAt, String verificationStatus, long recurrenceCount
    ) {
    }

    public record EffectCheck(
            String checkId, String metric, Double baselineValue, Double targetValue, Double postValue,
            OffsetDateTime windowFrom, OffsetDateTime windowTo, String result, OffsetDateTime checkedAt
    ) {
    }

    public record Review(
            String reviewId, String correctiveActionId, String rmaId, String rmaItemId, boolean lotProducedAfterAction,
            boolean symptomMatch, boolean causeMatch, String verdict, OffsetDateTime reviewedAt
    ) {
    }

    public record DailyTrend(LocalDate day, long count, boolean recurrence) {
    }
}
