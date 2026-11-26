package com.u1_2.effect.patternreport.domain;

import com.u1_2.effect.global.time.KoreaTime;
import com.u1_2.effect.pattern.domain.PatternRepository.Selection;
import com.u1_2.effect.patternreport.domain.PatternReportBody.Assessment;
import com.u1_2.effect.patternreport.domain.PatternReportBody.EffectivenessCheck;
import com.u1_2.effect.patternreport.domain.PatternReportBody.Finding;
import com.u1_2.effect.patternreport.domain.PatternReportBody.Material;
import com.u1_2.effect.patternreport.domain.PatternReportBody.Measurement;
import com.u1_2.effect.patternreport.domain.PatternReportBody.MonthCount;
import com.u1_2.effect.patternreport.domain.PatternReportBody.ProcessObservation;
import com.u1_2.effect.patternreport.domain.PatternReportBody.ProductCount;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PatternReportSourceRepository {

    private static final String ASSESSMENTS = """
            WITH assessments AS (
                SELECT 'OCCURRENCE' AS kind, a.cause_code AS code, m.cause_name AS name,
                       a.certainty_status AS status, a.process_id, a.equipment_id,
                       a.support_evidence_ids_json AS evidence
                FROM pcb.cause_assessment a JOIN pcb.cause_master m USING (cause_code)
                WHERE a.investigation_id = :investigationId AND a.assessed_at::timestamptz < :end
                  AND (a.certainty_status <> 'CONFIRMED' OR a.approved_at::timestamptz < :end)
                UNION ALL
                SELECT 'DETECTION_ESCAPE', a.escape_code, m.escape_name, a.status,
                       a.detection_process_id, NULL, a.evidence_ids_json
                FROM pcb.escape_assessment a JOIN pcb.escape_master m USING (escape_code)
                WHERE a.investigation_id = :investigationId AND a.assessed_at::timestamptz < :end
            )
            """;

    // 목록의 우선순위와 부록에 기록하는 반송품 재검에 같은 확정 근거를 사용함
    private static final String CONFIRMED_TESTS = """
            confirmed_tests AS (
                SELECT DISTINCT t.test_run_id, t.rma_item_id
                FROM pcb.cause_assessment a
                CROSS JOIN LATERAL jsonb_array_elements_text(cast(a.support_evidence_ids_json AS jsonb)) ref(id)
                JOIN pcb.evidence e ON e.evidence_id = ref.id AND e.source_type = 'test_run'
                JOIN pcb.test_run t ON t.test_run_id = e.source_record_id
                WHERE a.investigation_id = :investigationId AND e.investigation_id = :investigationId
                  AND a.certainty_status = 'CONFIRMED' AND a.approved_at::timestamptz < :end
                  AND e.available_at::timestamptz < :end AND t.ended_at::timestamptz < :end
                  AND t.phase = 'RMA_LAB' AND CAST(t.origin_verified AS text) IN ('1', 'true')
            )
            """;

    private final JdbcClient jdbc;

    public Quantities quantities(Selection selection) {
        return claims(selection, """
                SELECT coalesce(sum(i.claimed_defect_qty), 0) AS claimed, sum(i.verified_defect_qty) AS verified
                FROM items x JOIN pcb.rma_item i USING (rma_item_id)
                """).query(Quantities.class).single();
    }

    public List<MonthCount> months(Selection selection) {
        return claims(selection, """
                SELECT to_char(opened_at AT TIME ZONE 'Asia/Seoul', 'YYYY-MM') AS month, count(*) AS claims
                FROM claims GROUP BY 1 ORDER BY 1
                """).query(MonthCount.class).list();
    }

    public List<ProductCount> products(Selection selection) {
        return claims(selection, """
                SELECT c.reported_part_no AS part_no, count(DISTINCT c.rma_id) AS claims,
                       count(DISTINCT i.lot_id) AS lots
                FROM claims c LEFT JOIN items i USING (rma_id) GROUP BY 1 ORDER BY claims DESC, part_no
                """).query(ProductCount.class).list();
    }

    public List<Assessment> assessments(Selection selection) {
        return selection.query(jdbc, ASSESSMENTS + """
                SELECT kind, code, name, status, process_id, equipment_id FROM assessments
                ORDER BY kind DESC, (status = 'CONFIRMED') DESC, code, process_id, equipment_id
                """).query(Assessment.class).list();
    }

    public List<Finding> findings(Selection selection) {
        // 반송품 검사값은 부록에 넣고 본문에는 조사 판정에 직접 연결된 공정·설비·비교시험 기록을 사용함
        return selection.query(jdbc, ASSESSMENTS + """
                SELECT DISTINCT a.kind, e.source_type, e.content_summary AS summary
                FROM assessments a
                CROSS JOIN LATERAL jsonb_array_elements_text(cast(a.evidence AS jsonb)) ref(id)
                JOIN pcb.evidence e ON e.evidence_id = ref.id
                WHERE e.investigation_id = :investigationId AND e.available_at::timestamptz < :end
                  AND (e.source_type <> 'test_run' OR EXISTS (
                      SELECT 1 FROM pcb.test_run t WHERE t.test_run_id = e.source_record_id AND t.rma_item_id IS NULL
                  ))
                ORDER BY kind DESC, source_type, summary
                """).query(Finding.class).list();
    }

    public List<Process> processes() {
        return jdbc.sql("""
                SELECT process_id, process_name AS name, sequence_no AS sequence,
                       CAST(is_inspection AS text) IN ('1', 'true') AS inspection
                FROM pcb.process_master ORDER BY sequence_no, process_id
                """).query(Process.class).list();
    }

    public List<Material> materials(Selection selection) {
        return claims(selection, """
                SELECT m.material_lot_id, m.material_type, s.supplier_name AS supplier,
                       m.iqc_result AS inspection_result, count(DISTINCT i.lot_id) AS affected_lots
                FROM items i JOIN pcb.material_usage u USING (lot_id)
                JOIN pcb.material_lot m USING (material_lot_id) JOIN pcb.supplier s USING (supplier_id)
                WHERE u.issued_at::timestamptz < :end
                GROUP BY 1, 2, 3, 4 ORDER BY affected_lots DESC, m.material_lot_id
                """).query(Material.class).list();
    }

    public List<ActionRow> actions(Selection selection) {
        return selection.query(jdbc, """
                SELECT corrective_action_id AS action_id, owner_dept AS department, description,
                       due_at::timestamptz AS due_at,
                       CASE WHEN implemented_at::timestamptz < :end THEN implemented_at::timestamptz END AS implemented_at,
                       verification_status
                FROM pcb.corrective_action
                WHERE investigation_id = :investigationId AND selected_at::timestamptz < :end
                ORDER BY selected_at::timestamptz, corrective_action_id
                """).query(ActionRow.class).list();
    }

    public List<ActionCheck> checks(Selection selection) {
        return selection.query(jdbc, """
                SELECT a.corrective_action_id AS action_id, e.metric, e.baseline_value AS baseline,
                       e.target_value AS target, e.post_value AS measured, e.window_to::timestamptz AS window_to, e.result
                FROM pcb.effectiveness_check e JOIN pcb.corrective_action a USING (corrective_action_id)
                WHERE a.investigation_id = :investigationId AND e.checked_at::timestamptz < :end
                ORDER BY e.checked_at::timestamptz, e.check_id
                """).query((row, index) -> new ActionCheck(row.getString("action_id"),
                new EffectivenessCheck(row.getString("metric"), row.getBigDecimal("baseline"),
                        row.getBigDecimal("target"), row.getBigDecimal("measured"),
                        KoreaTime.inSeoul(row.getObject("window_to", OffsetDateTime.class)), row.getString("result"))))
                .list();
    }

    public List<CaseRow> evidenceCases(Selection selection, int limit) {
        // 확정 원인과 연결된 반송품 근거를 우선하고 부족한 경우 실제 조사 연결 건으로 채움
        return claims(selection, ", " + CONFIRMED_TESTS + """
                SELECT c.rma_id, (c.opened_at AT TIME ZONE 'Asia/Seoul')::date AS opened_on,
                       c.reported_part_no AS part_no, r.detection_stage, r.qa_status,
                       link.membership_reason, EXISTS (
                           SELECT 1 FROM items i JOIN confirmed_tests t USING (rma_item_id) WHERE i.rma_id = c.rma_id
                       ) AS confirmed_test
                FROM claims c JOIN pcb.rma_claim r USING (rma_id)
                JOIN LATERAL (
                    SELECT membership_reason FROM pcb.investigation_claim m
                    WHERE m.investigation_id = :investigationId AND m.rma_id = c.rma_id
                      AND m.linked_at::timestamptz < :end
                    ORDER BY m.linked_at::timestamptz DESC, m.link_id DESC LIMIT 1
                ) link ON true
                ORDER BY confirmed_test DESC, c.opened_at, c.rma_id LIMIT :limit
                """).param("limit", limit).query(CaseRow.class).list();
    }

    public List<ItemRow> items(Selection selection, String rmaId) {
        return selection.query(jdbc, """
                SELECT i.rma_item_id, i.lot_id, symptom.name AS symptom, i.claimed_defect_qty AS claimed_quantity,
                       i.returned_qty AS returned_quantity, i.verified_defect_qty AS verified_quantity
                FROM pcb.rma_item i
                LEFT JOIN LATERAL (
                    SELECT d.minor_name AS name FROM pcb.symptom_assessment s
                    JOIN pcb.symptom_definition d ON d.minor_code = s.primary_label
                    WHERE s.rma_item_id = i.rma_item_id AND s.created_at::timestamptz < :end
                    ORDER BY s.created_at::timestamptz DESC, s.assessment_id DESC LIMIT 1
                ) symptom ON true
                WHERE i.rma_id = :rmaId ORDER BY i.rma_item_id
                """).param("rmaId", rmaId).query(ItemRow.class).list();
    }

    public List<Measurement> measurements(Selection selection, String itemId) {
        return selection.query(jdbc, "WITH " + CONFIRMED_TESTS + """
                , selected_test AS (
                    SELECT t.test_run_id FROM pcb.test_run t
                    WHERE t.rma_item_id = :itemId AND t.phase = 'RMA_LAB'
                      AND t.ended_at::timestamptz < :end AND CAST(t.origin_verified AS text) IN ('1', 'true')
                    ORDER BY EXISTS (SELECT 1 FROM confirmed_tests c WHERE c.test_run_id = t.test_run_id) DESC,
                             t.ended_at::timestamptz DESC, t.test_run_id DESC LIMIT 1
                )
                SELECT s.test_name AS name, m.raw_value AS value, m.unit, m.result
                FROM selected_test t JOIN pcb.test_measurement m USING (test_run_id)
                JOIN pcb.test_spec s USING (test_spec_id)
                ORDER BY s.test_name, m.trial_index, m.measurement_id
                """).param("itemId", itemId).query(Measurement.class).list();
    }

    public List<ProcessObservation> observations(Selection selection, String itemId) {
        // 해당 Lot가 접수되기 전에 실행된 공정 중 확정 원인 공정의 실제 관측값만 연결함
        return selection.query(jdbc, """
                SELECT DISTINCT ON (run.process_id, o.item_code) run.process_id, run.equipment_id,
                       o.item_code, o.value, o.unit, o.lower_limit, o.upper_limit, o.spc_flag
                FROM pcb.rma_item i JOIN pcb.rma_claim r USING (rma_id) JOIN pcb.process_run run USING (lot_id)
                JOIN pcb.process_observation o USING (process_run_id)
                WHERE i.rma_item_id = :itemId AND run.ended_at::timestamptz <= r.opened_at::timestamptz
                  AND o.measured_at::timestamptz <= r.opened_at::timestamptz
                  AND EXISTS (
                      SELECT 1 FROM pcb.cause_assessment a
                      WHERE a.investigation_id = :investigationId AND a.process_id = run.process_id
                        AND a.certainty_status = 'CONFIRMED' AND a.approved_at::timestamptz < :end
                  )
                ORDER BY run.process_id, o.item_code, o.measured_at::timestamptz DESC, o.observation_id DESC
                """).param("itemId", itemId).query(ProcessObservation.class).list();
    }

    private JdbcClient.StatementSpec claims(Selection selection, String sql) {
        return selection.scopedClaims(jdbc, sql);
    }

    public record Quantities(long claimed, Long verified) {
    }

    public record Process(String processId, String name, int sequence, boolean inspection) {
    }

    public record ActionRow(
            String actionId, String department, String description, OffsetDateTime dueAt,
            OffsetDateTime implementedAt, String verificationStatus
    ) {
    }

    public record ActionCheck(String actionId, EffectivenessCheck check) {
    }

    public record CaseRow(String rmaId, LocalDate openedOn, String partNo, String detectionStage, String qaStatus,
                          String membershipReason, boolean confirmedTest) {
    }

    public record ItemRow(String rmaItemId, String lotId, String symptom, long claimedQuantity,
                          long returnedQuantity, Long verifiedQuantity) {
    }
}
