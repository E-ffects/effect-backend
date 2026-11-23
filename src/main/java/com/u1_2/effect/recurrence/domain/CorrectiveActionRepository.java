package com.u1_2.effect.recurrence.domain;

import com.u1_2.effect.global.time.KoreaTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CorrectiveActionRepository {

    private static final String DETAILS = """
            SELECT corrective_action_id, investigation_id, action_role, action_category, description,
                   owner_dept, cause_code, escape_code, selected_at::timestamptz AS selected_at,
                   due_at::timestamptz AS due_at, implemented_at::timestamptz AS implemented_at,
                   verification_status, scope_json AS scope
            FROM pcb.corrective_action WHERE corrective_action_id = :actionId
            """;

    private final JdbcClient jdbcClient;

    public Optional<Detail> find(String actionId) {
        return jdbcClient.sql(DETAILS).param("actionId", actionId).query(Detail.class).optional();
    }

    public Optional<Detail> lock(String actionId) {
        return jdbcClient.sql(DETAILS + " FOR UPDATE").param("actionId", actionId).query(Detail.class).optional();
    }

    public boolean investigationExists(String id) {
        return jdbcClient.sql("SELECT EXISTS (SELECT 1 FROM pcb.investigation WHERE investigation_id = :id)")
                .param("id", id).query(Boolean.class).single();
    }

    public boolean codesExist(CorrectiveActionPlan plan) {
        return jdbcClient.sql("""
                SELECT (CAST(:cause AS text) IS NULL OR EXISTS (
                           SELECT 1 FROM pcb.cause_master WHERE cause_code = :cause
                       )) AND (CAST(:escape AS text) IS NULL OR EXISTS (
                           SELECT 1 FROM pcb.escape_master WHERE escape_code = :escape
                       ))
                """).param("cause", plan.causeCode()).param("escape", plan.escapeCode())
                .query(Boolean.class).single();
    }

    public boolean hasHistory(String id) {
        return jdbcClient.sql("""
                SELECT EXISTS (SELECT 1 FROM pcb.effectiveness_check WHERE corrective_action_id = :id)
                    OR EXISTS (SELECT 1 FROM pcb.recurrence_review WHERE corrective_action_id = :id)
                """).param("id", id).query(Boolean.class).single();
    }

    public void create(String id, String investigationId, CorrectiveActionPlan plan, OffsetDateTime selectedAt) {
        withPlan("""
                INSERT INTO pcb.corrective_action (
                    corrective_action_id, investigation_id, action_role, action_category, description, owner_dept,
                    cause_code, escape_code, scope_json, selected_at, due_at, verification_status)
                VALUES (:id, :investigationId, :role, :category, :description, :owner,
                        :cause, :escape, :scope, :selectedAt, :dueAt, 'IMPLEMENTING')
                """, plan).param("id", id).param("investigationId", investigationId)
                .param("selectedAt", KoreaTime.formatPcbTime(selectedAt)).update();
    }

    public void update(String id, CorrectiveActionPlan plan) {
        withPlan("""
                UPDATE pcb.corrective_action SET action_role = :role, action_category = :category,
                    description = :description, owner_dept = :owner, cause_code = :cause, escape_code = :escape,
                    scope_json = :scope, due_at = :dueAt
                WHERE corrective_action_id = :id
                """, plan).param("id", id).update();
    }

    public void implement(String id, OffsetDateTime implementedAt) {
        jdbcClient.sql("""
                UPDATE pcb.corrective_action SET implemented_at = :implementedAt, verification_status = 'MONITORING'
                WHERE corrective_action_id = :id
                """).param("id", id).param("implementedAt", KoreaTime.formatPcbTime(implementedAt)).update();
    }

    public boolean reserve(long userId, String key, String actionId, String payload) {
        // 등록 예약과 원본 대책을 같은 트랜잭션에서 저장함. 실패하면 둘 다 롤백됨
        return registration("""
                INSERT INTO app.corrective_action_registration (user_id, request_key, corrective_action_id, request_payload)
                VALUES (:userId, :key, :actionId, CAST(:payload AS jsonb))
                ON CONFLICT (user_id, request_key) DO NOTHING
                """, userId, key, payload).param("actionId", actionId).update() == 1;
    }

    public Optional<Registration> findRegistration(long userId, String key, String payload) {
        return registration("""
                SELECT corrective_action_id, request_payload = CAST(:payload AS jsonb) AS same_request
                FROM app.corrective_action_registration WHERE user_id = :userId AND request_key = :key
                """, userId, key, payload).query(Registration.class).optional();
    }

    private JdbcClient.StatementSpec registration(String sql, long userId, String key, String payload) {
        return jdbcClient.sql(sql).param("userId", userId).param("key", key).param("payload", payload);
    }

    private JdbcClient.StatementSpec withPlan(String sql, CorrectiveActionPlan plan) {
        return jdbcClient.sql(sql).param("role", plan.actionRole().name()).param("category", plan.actionCategory().name())
                .param("description", plan.description()).param("owner", plan.ownerDept())
                .param("cause", plan.causeCode()).param("escape", plan.escapeCode()).param("scope", plan.scope().toString())
                .param("dueAt", KoreaTime.formatPcbTime(plan.dueAt()));
    }

    public record Detail(
            String correctiveActionId, String investigationId, String actionRole, String actionCategory,
            String description, String ownerDept, String causeCode, String escapeCode, OffsetDateTime selectedAt,
            OffsetDateTime dueAt, OffsetDateTime implementedAt, String verificationStatus, String scope
    ) {
    }

    public record Registration(String correctiveActionId, boolean sameRequest) {
    }
}
