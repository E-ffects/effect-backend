package com.u1_2.effect.rma.domain;

import com.u1_2.effect.global.time.KoreaTime;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RmaListRepository {

    // 목록과 CSV가 같은 품목 집계·최신 상태·필터를 사용하도록 조회 조건을 공유함
    private static final String FILTERED_CLAIMS = """
            WITH claims AS (
                SELECT c.rma_id, CAST(c.opened_at AS timestamptz) AS opened_at,
                       u.customer_name, c.reported_part_no, c.detection_stage,
                       COALESCE(s.major_name, i.classification_json ->> 'major_name') AS major_name,
                       COALESCE(s.minor_name, i.classification_json ->> 'minor_name') AS minor_name,
                       COALESCE(i.classification_json ->> 'status', s.status) AS classification_status,
                       COALESCE(qty.claimed_defect_qty, 0) AS claimed_defect_qty,
                       t.severity::integer AS severity, t.analysis_level, c.cs_status, c.qa_status,
                       COALESCE(i.intake_state, 'ACCEPTED') AS intake_state,
                       COALESCE(a.analysis_state, 'NOT_REQUESTED') AS analysis_state,
                       COALESCE(d.approval_status, 'NOT_REQUIRED') AS approval_status,
                       COALESCE(r.verdict, a.result_json #>> '{recurrence_check,status}') AS recurrence_verdict
                FROM pcb.rma_claim c
                JOIN pcb.customer u ON u.customer_id = c.customer_id
                LEFT JOIN app.intake i ON i.rma_id = c.rma_id
                LEFT JOIN (
                    SELECT rma_id, sum(claimed_defect_qty)::bigint AS claimed_defect_qty
                    FROM pcb.rma_item GROUP BY rma_id
                ) qty ON qty.rma_id = c.rma_id
                LEFT JOIN (
                    SELECT DISTINCT ON (ri.rma_id) ri.rma_id, d.major_name, d.minor_name, symptom.status
                    FROM pcb.rma_item ri
                    JOIN pcb.symptom_assessment symptom USING (rma_item_id)
                    JOIN pcb.symptom_definition d ON d.minor_code = symptom.primary_label
                    ORDER BY ri.rma_id, %s
                ) s ON s.rma_id = c.rma_id
                LEFT JOIN (
                    SELECT DISTINCT ON (rma_id) rma_id, severity, analysis_level FROM pcb.qa_triage triage
                    ORDER BY rma_id, %s
                ) t ON t.rma_id = c.rma_id
                LEFT JOIN (
                    SELECT DISTINCT ON (rma_id) rma_id, analysis_id, analysis_state, result_json
                    FROM app.ai_analysis analysis
                    ORDER BY rma_id, %s
                ) a ON a.rma_id = c.rma_id
                LEFT JOIN app.cause_decision d ON d.analysis_id = a.analysis_id
                LEFT JOIN (
                    SELECT DISTINCT ON (ri.rma_id) ri.rma_id, rr.verdict FROM pcb.rma_item ri
                    JOIN pcb.recurrence_review rr USING (rma_item_id)
                    ORDER BY ri.rma_id, rr.reviewed_at DESC, rr.review_id DESC
                ) r ON r.rma_id = c.rma_id
            ), filtered AS (
                SELECT *
                FROM claims
                WHERE (CAST(:q AS text) IS NULL OR position(lower(:q) IN
                       lower(concat_ws(' ', rma_id, customer_name, reported_part_no, major_name, minor_name))) > 0)
                  AND (CAST(:product AS text) IS NULL OR reported_part_no = :product)
                  AND (CAST(:defectType AS text) IS NULL OR major_name = :defectType)
                  AND (CAST(:classification AS text) IS NULL OR classification_status = :classification)
                  AND (CAST(:status AS text) IS NULL OR cs_status = :status)
                  AND (CAST(:severity AS integer) IS NULL OR severity = :severity)
                  AND (CAST(:recurrence AS text) IS NULL OR recurrence_verdict = :recurrence)
                  AND (CAST(:from AS date) IS NULL OR (opened_at AT TIME ZONE 'Asia/Seoul')::date >= :from)
                  AND (CAST(:to AS date) IS NULL OR (opened_at AT TIME ZONE 'Asia/Seoul')::date <= :to)
                  AND (CAST(:overDays AS integer) IS NULL OR (cs_status <> 'CLOSED' AND
                       CAST(:today AS date) - (opened_at AT TIME ZONE 'Asia/Seoul')::date >= :overDays))
            )
            """.formatted(RmaHistoryOrder.SYMPTOM, RmaHistoryOrder.TRIAGE, RmaHistoryOrder.ANALYSIS);

    private final JdbcClient jdbcClient;

    public Result search(Filter filter, LocalDate today, List<Sort> sort, int page, int size, String open) {
        var rows = filteredClaims("""
                , ranked AS (
                    SELECT *, row_number() OVER (ORDER BY %s) AS position FROM filtered
                ), summary AS (
                    SELECT count(*) AS total, max(position) FILTER (WHERE rma_id = :open) AS open_position
                    FROM ranked
                ), selected AS (
                    SELECT total, COALESCE((open_position - 1) / :size + 1, :page) AS page,
                           open_position IS NOT NULL AS open_found,
                           EXISTS(SELECT 1 FROM pcb.rma_claim WHERE rma_id = :open) AS open_exists
                    FROM summary
                )
                SELECT r.*, s.total, s.page, s.open_found, s.open_exists
                FROM selected s LEFT JOIN ranked r
                    ON r.position > (s.page - 1) * :size AND r.position <= s.page * :size
                ORDER BY r.position
                """.formatted(orderBy(sort)), filter, today)
                .param("open", open).param("page", page).param("size", size)
                .query((row, index) -> new PageRow(row.getString("rma_id") == null ? null : readClaim(row),
                        row.getLong("total"), row.getLong("page"), row.getBoolean("open_found"),
                        row.getBoolean("open_exists"))).list();
        // 요청한 행이 현재 필터에 포함되면 그 행이 속한 페이지를 반환함
        var summary = rows.getFirst();
        List<Claim> claims = rows.stream().map(PageRow::claim).filter(Objects::nonNull).toList();
        return new Result(claims, summary.total(), summary.page(),
                open == null ? null : summary.openFound(), open == null ? null : summary.openExists());
    }

    public List<Claim> findAll(Filter filter, LocalDate today, List<Sort> sort) {
        return filteredClaims("SELECT * FROM filtered ORDER BY " + orderBy(sort), filter, today)
                .query((row, index) -> readClaim(row)).list();
    }

    private JdbcClient.StatementSpec filteredClaims(String query, Filter filter, LocalDate today) {
        return jdbcClient.sql(FILTERED_CLAIMS + query)
                .param("q", filter.q()).param("product", filter.product()).param("defectType", filter.defectType())
                .param("classification", filter.classification()).param("status", filter.status())
                .param("severity", filter.severity()).param("recurrence", filter.recurrence())
                .param("from", filter.from()).param("to", filter.to()).param("overDays", filter.overDays())
                .param("today", today);
    }

    private String orderBy(List<Sort> sort) {
        String ordering = sort.stream()
                .map(order -> order.column().apiName() + (order.descending() ? " DESC" : " ASC") + " NULLS LAST")
                .collect(Collectors.joining(", "));
        if (sort.stream().noneMatch(order -> order.column() == SortColumn.RMA_ID)) {
            ordering += ", rma_id ASC";
        }
        return ordering;
    }

    private Claim readClaim(ResultSet row) throws SQLException {
        return new Claim(row.getString("rma_id"), KoreaTime.inSeoul(row.getObject("opened_at", OffsetDateTime.class)),
                row.getString("customer_name"), row.getString("reported_part_no"), row.getString("detection_stage"),
                row.getString("major_name"), row.getString("minor_name"), row.getString("classification_status"),
                row.getLong("claimed_defect_qty"), row.getObject("severity", Integer.class),
                row.getString("analysis_level"), row.getString("cs_status"), row.getString("qa_status"),
                row.getString("intake_state"), row.getString("analysis_state"), row.getString("approval_status"),
                row.getString("recurrence_verdict"));
    }

    // 검색 조건은 페이지 번호와 독립적임. 페이지를 옮겨도 같은 조건을 다시 사용함
    public record Filter(
            String q, String product, String defectType, String classification, String status, Integer severity,
            String recurrence, LocalDate from, LocalDate to, Integer overDays
    ) {
    }

    @Getter
    @Accessors(fluent = true)
    @RequiredArgsConstructor
    public enum SortColumn {
        RMA_ID("rma_id"), OPENED_AT("opened_at"), CUSTOMER_NAME("customer_name"), REPORTED_PART_NO("reported_part_no"),
        DETECTION_STAGE("detection_stage"), MAJOR_NAME("major_name"), MINOR_NAME("minor_name"),
        CLASSIFICATION_STATUS("classification_status"), CLAIMED_DEFECT_QTY("claimed_defect_qty"),
        SEVERITY("severity"), CS_STATUS("cs_status"), ANALYSIS_LEVEL("analysis_level"),
        ANALYSIS_STATE("analysis_state");

        private final String apiName;
    }

    public record Sort(SortColumn column, boolean descending) {
    }

    public record Claim(
            String rmaId, OffsetDateTime openedAt, String customerName, String reportedPartNo,
            String detectionStage, String majorName, String minorName, String classificationStatus,
            long claimedDefectQty, Integer severity, String analysisLevel, String csStatus,
            String qaStatus, String intakeState, String analysisState, String approvalStatus, String recurrenceVerdict
    ) {
    }

    public record Result(List<Claim> items, long total, long page, Boolean openFound, Boolean openExists) {
    }

    private record PageRow(Claim claim, long total, long page, boolean openFound, boolean openExists) {
    }
}
