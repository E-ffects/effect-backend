package com.u1_2.effect.rma.domain.review;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RmaInvestigationRepository {

    private final JdbcClient jdbcClient;

    public void lockCauseMatching() {
        // 서로 다른 접수에서 같은 원인을 동시에 확정해도 조사 검색·번호 발급을 순서대로 수행함
        jdbcClient.sql("SELECT pg_advisory_xact_lock(14532)").query((row, index) -> true).single();
    }

    public Optional<Assessment> findAssessment(String causeCode, String equipmentId) {
        return jdbcClient.sql("""
                SELECT investigation_id, cause_assessment_id FROM pcb.cause_assessment
                WHERE cause_code = :causeCode AND approved_at IS NOT NULL
                    AND (CAST(:equipmentId AS text) IS NULL OR equipment_id = :equipmentId)
                ORDER BY CAST(approved_at AS timestamptz) DESC, cause_assessment_id DESC LIMIT 1
                """).param("causeCode", causeCode).param("equipmentId", equipmentId).query(Assessment.class).optional();
    }

    public long nextInvestigationNumber() {
        return jdbcClient.sql("""
                SELECT COALESCE(MAX(SUBSTRING(investigation_id FROM 8)::BIGINT), 0) + 1
                FROM pcb.investigation WHERE investigation_id ~ '^INV-26-[0-9]+$'
                """).query(Long.class).single();
    }

    public void insertInvestigation(NewCause row) {
        jdbcClient.sql("""
                INSERT INTO pcb.investigation
                    (investigation_id, title, analysis_level, scope_definition, owner, opened_at, status)
                VALUES (:investigationId, :title, :level, :scope, :owner, :now, 'ANALYSIS')
                """).paramSource(row).update();
        jdbcClient.sql("""
                INSERT INTO pcb.cause_assessment (cause_assessment_id, investigation_id, cause_code, cause_category,
                    process_id, equipment_id, material_lot_ids_json, certainty_status, support_evidence_ids_json,
                    assessed_at, approved_at)
                SELECT :causeAssessmentId, :investigationId, cause_code, cause_category, COALESCE(:processId, process_id),
                    :equipmentId, '[]', 'PROBABLE', '[]', :now, :now
                FROM pcb.cause_master WHERE cause_code = :causeCode
                """).paramSource(row).update();
    }

    public Optional<String> findCauseName(String causeCode) {
        return jdbcClient.sql("SELECT cause_name FROM pcb.cause_master WHERE cause_code = :code")
                .param("code", causeCode).query(String.class).optional();
    }

    public void linkClaim(String rmaId, Assessment assessment, String linkId, String claimCauseId, String rationale, String now) {
        jdbcClient.sql("""
                INSERT INTO pcb.investigation_claim (link_id, investigation_id, rma_id, membership_reason, linked_at)
                SELECT :linkId, :investigationId, :rmaId, 'AI 원인 후보 채택', :now
                WHERE NOT EXISTS (SELECT 1 FROM pcb.investigation_claim
                                  WHERE rma_id = :rmaId AND investigation_id = :investigationId)
                """).param("linkId", linkId).param("investigationId", assessment.investigationId())
                .param("rmaId", rmaId).param("now", now).update();
        jdbcClient.sql("""
                INSERT INTO pcb.claim_cause (claim_cause_id, rma_item_id, cause_assessment_id, applicability, rationale, decided_at)
                SELECT :claimCauseId || ri.rma_item_id, ri.rma_item_id, :assessmentId, 'APPLIES', :rationale, :now
                FROM pcb.rma_item ri WHERE ri.rma_id = :rmaId
                    AND NOT EXISTS (SELECT 1 FROM pcb.claim_cause
                        WHERE rma_item_id = ri.rma_item_id AND cause_assessment_id = :assessmentId AND applicability = 'APPLIES')
                """).param("claimCauseId", claimCauseId).param("assessmentId", assessment.causeAssessmentId())
                .param("rmaId", rmaId).param("rationale", rationale).param("now", now).update();
    }

    public record Assessment(String investigationId, String causeAssessmentId) {
    }

    public record NewCause(String investigationId, String causeAssessmentId, String causeCode, String title,
                           String processId, String equipmentId, String level, String scope, String owner, String now) {
    }
}
