package com.u1_2.effect.alert.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AlertRepository {

    private static final String VISIBLE = """
            FROM app.alert a
            LEFT JOIN app.alert_user_state s ON s.alert_id = a.id AND s.user_id = :userId
            WHERE s.deleted_at IS NULL
            """;

    private static final String ITEM = """
            SELECT a.id, a.type, a.rma_id, a.corrective_action_id, a.pattern_id, a.target_label, a.reason, a.check_items,
                   a.created_at, s.read_at IS NOT NULL AS read, coalesce(s.starred, false) AS starred
            """ + VISIBLE;

    private final JdbcClient jdbcClient;

    public List<Item> list(long userId, String type, boolean starred, Long afterId, int limit) {
        // 커서 이후 조회는 오래된 신규 ID부터 읽고, 일반 목록은 미확인과 최근 알림을 우선함
        String order = afterId == null ? "read, a.created_at DESC, a.id DESC" : "a.id";
        return forUser(ITEM + """
                AND (CAST(:type AS text) IS NULL OR a.type = :type)
                AND (:starred = false OR s.starred)
                AND (CAST(:afterId AS bigint) IS NULL OR a.id > :afterId)
                ORDER BY %s LIMIT :limit
                """.formatted(order), userId).param("type", type).param("starred", starred)
                .param("afterId", afterId).param("limit", limit).query(Item.class).list();
    }

    public Optional<Item> find(long userId, long id) {
        return forUser(ITEM + " AND a.id = :id", userId).param("id", id).query(Item.class).optional();
    }

    public List<Counts> counts(long userId) {
        return forUser("""
                SELECT a.type, count(*) FILTER (WHERE s.read_at IS NULL) AS unread,
                       count(*) FILTER (WHERE s.starred) AS starred
                """ + VISIBLE + " GROUP BY a.type", userId).query(Counts.class).list();
    }

    public long maxId() {
        return jdbcClient.sql("SELECT coalesce(max(id), 0) FROM app.alert").query(Long.class).single();
    }

    public int apply(long userId, List<Long> ids, Change change) {
        return update(" AND a.id IN (:ids)", userId, change).param("ids", ids).update();
    }

    public void readAll(long userId) {
        update(" AND s.read_at IS NULL", userId, Change.READ).update();
    }

    private JdbcClient.StatementSpec update(String selection, long userId, Change change) {
        // 계정 상태만 바꾸고, 지운 이벤트는 동시 요청이 도착해도 다시 살리지 않음
        return forUser("""
                INSERT INTO app.alert_user_state AS saved (user_id, alert_id, read_at, starred, deleted_at)
                SELECT :userId, a.id, CASE WHEN :markRead THEN CURRENT_TIMESTAMP END, :setStar,
                       CASE WHEN :markDeleted THEN CURRENT_TIMESTAMP END
                """ + VISIBLE + selection + "\n" + """
                ORDER BY a.id
                ON CONFLICT (user_id, alert_id) DO UPDATE
                SET read_at = coalesce(saved.read_at, EXCLUDED.read_at),
                    starred = CASE WHEN :changeStar THEN EXCLUDED.starred ELSE saved.starred END,
                    deleted_at = EXCLUDED.deleted_at, updated_at = CURRENT_TIMESTAMP
                WHERE saved.deleted_at IS NULL
                """, userId).param("markRead", change == Change.READ).param("markDeleted", change == Change.DELETE)
                .param("changeStar", change == Change.STAR || change == Change.UNSTAR).param("setStar", change == Change.STAR);
    }

    private JdbcClient.StatementSpec forUser(String sql, long userId) {
        return jdbcClient.sql(sql).param("userId", userId);
    }

    public enum Change { READ, DELETE, STAR, UNSTAR }

    public record Item(
            long id, String type, String rmaId, String correctiveActionId, String patternId, String targetLabel,
            String reason, String checkItems, OffsetDateTime createdAt, boolean read, boolean starred
    ) {
    }

    public record Counts(String type, long unread, long starred) {
    }
}
