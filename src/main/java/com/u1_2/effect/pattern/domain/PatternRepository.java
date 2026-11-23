package com.u1_2.effect.pattern.domain;

import com.u1_2.effect.rma.domain.RmaHistoryOrder;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PatternRepository {

    // 조사의 연결 이력이 중복돼도 근거와 집계에는 접수당 한 행만 사용함
    private static final String SCOPED_CLAIMS = """
            WITH claims AS (
                SELECT c.rma_id, c.opened_at::timestamptz AS opened_at
                FROM pcb.rma_claim c
                WHERE c.opened_at::timestamptz >= :start AND c.opened_at::timestamptz < :end
                  AND (CAST(:product AS text) IS NULL OR c.reported_part_no = :product)
                  AND (CAST(:investigationId AS text) IS NULL OR EXISTS (
                      SELECT 1 FROM pcb.investigation_claim ic
                      WHERE ic.investigation_id = :investigationId AND ic.rma_id = c.rma_id
                        AND ic.linked_at::timestamptz < :end
                  ))
            ), items AS (
                SELECT i.rma_item_id, i.rma_id, i.lot_id
                FROM claims c JOIN pcb.rma_item i USING (rma_id)
            )
            """;

    private final JdbcClient jdbcClient;

    public Optional<Profile> findProfile(Selection selection) {
        return query(selection, """
                SELECT i.investigation_id, i.title, i.analysis_level, i.status,
                       (SELECT coalesce(sum(l.amount_krw), 0)
                        FROM pcb.loss_ledger l WHERE l.investigation_id = i.investigation_id
                          AND l.occurred_at::timestamptz >= :start AND l.occurred_at::timestamptz < :end) AS loss
                FROM pcb.investigation i WHERE i.investigation_id = :investigationId
                """).query(Profile.class).optional();
    }

    public List<CauseCount> findCauses(Selection selection) {
        // 접수의 여러 품목에 원인이 기록돼도 기간 말까지 확정·적용된 최신 원인 하나만 집계함
        return query(selection, SCOPED_CLAIMS + """
                , confirmed_causes AS (
                    SELECT DISTINCT ON (i.rma_id) i.rma_id, m.cause_name
                    FROM items i
                    JOIN pcb.claim_cause cc USING (rma_item_id)
                    JOIN pcb.cause_assessment a USING (cause_assessment_id)
                    JOIN pcb.cause_master m USING (cause_code)
                    WHERE cc.applicability IN ('APPLIES', 'APPLIES_PHOTO_ONLY')
                      AND a.certainty_status = 'CONFIRMED'
                      AND a.approved_at::timestamptz < :end AND cc.decided_at::timestamptz < :end
                      AND (CAST(:investigationId AS text) IS NULL OR a.investigation_id = :investigationId)
                    ORDER BY i.rma_id, cc.decided_at::timestamptz DESC, cc.claim_cause_id DESC
                )
                SELECT x.cause_name AS cause, count(*) AS count
                FROM claims c LEFT JOIN confirmed_causes x USING (rma_id)
                GROUP BY x.cause_name ORDER BY count DESC, cause NULLS LAST
                """).query(CauseCount.class).list();
    }

    public List<Evidence> findEvidence(Selection selection) {
        return findEvidence(selection, null);
    }

    public List<Evidence> findEvidence(Selection selection, Integer limit) {
        return query(selection, SCOPED_CLAIMS + """
                , latest_triage AS (
                    SELECT DISTINCT ON (triage.rma_id) triage.rma_id, triage.severity
                    FROM pcb.qa_triage triage JOIN claims c USING (rma_id)
                    WHERE triage.decided_at::timestamptz < :end
                    ORDER BY triage.rma_id, %s
                )
                SELECT c.rma_id, c.opened_at, coalesce(t.severity, 0) AS severity
                FROM claims c LEFT JOIN latest_triage t USING (rma_id)
                ORDER BY c.opened_at DESC, c.rma_id
                LIMIT :limit
                """.formatted(RmaHistoryOrder.TRIAGE)).param("limit", limit).query(Evidence.class).list();
    }

    public List<LotCount> findLots(Selection selection) {
        return query(selection, SCOPED_CLAIMS + """
                SELECT lot_id AS lot, count(DISTINCT rma_id) AS count
                FROM items WHERE lot_id IS NOT NULL
                GROUP BY lot_id ORDER BY count DESC, lot
                """).query(LotCount.class).list();
    }

    public List<EquipmentCount> findEquipment(Selection selection) {
        // 연결된 제조 Lot의 실제 공정 실행 기록을 사용함. 설비마다 같은 RMA는 한 번만 셈
        return query(selection, SCOPED_CLAIMS + """
                SELECT run.equipment_id, count(DISTINCT i.rma_id) AS count
                FROM items i JOIN claims c USING (rma_id)
                JOIN pcb.process_run run ON run.lot_id = i.lot_id AND run.ended_at::timestamptz <= c.opened_at
                GROUP BY run.equipment_id ORDER BY count DESC, run.equipment_id
                """).query(EquipmentCount.class).list();
    }

    public List<Action> findActions(Selection selection) {
        return query(selection, """
                SELECT corrective_action_id, action_category, description, verification_status,
                       implemented_at::timestamptz AS implemented_at
                FROM pcb.corrective_action
                WHERE investigation_id = :investigationId AND selected_at::timestamptz < :end
                ORDER BY selected_at::timestamptz DESC, corrective_action_id
                """).query(Action.class).list();
    }

    private JdbcClient.StatementSpec query(Selection selection, String sql) {
        return jdbcClient.sql(sql).param("start", selection.start()).param("end", selection.end())
                .param("product", selection.product()).param("investigationId", selection.investigationId());
    }

    public record Selection(OffsetDateTime start, OffsetDateTime end, String product, String investigationId) {
    }

    public record Profile(String investigationId, String title, String analysisLevel, String status, long loss) {
    }

    public record CauseCount(String cause, long count) {
    }

    public record Evidence(String rmaId, OffsetDateTime openedAt, int severity) {
    }

    public record LotCount(String lot, long count) {
    }

    public record EquipmentCount(String equipmentId, long count) {
    }

    public record Action(String correctiveActionId, String actionCategory, String description,
                         String verificationStatus, OffsetDateTime implementedAt) {
    }
}
