package com.u1_2.effect.report.domain;

import com.u1_2.effect.global.dto.PageParameters;
import com.u1_2.effect.rma.domain.RmaHistoryOrder;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ReportRepository {

    private static final String REPORT_COLUMNS = """
            rma_id, product, defect_type, claim_date, severity, claim_status,
            generated_at, reviewed_by, reviewed_at
            """;

    private final JdbcClient jdbcClient;

    public Optional<ClaimHeader> header(String rmaId) {
        return jdbcClient.sql("""
                SELECT c.reported_part_no AS product, c.cs_status AS claim_status,
                       (c.opened_at::timestamptz AT TIME ZONE 'Asia/Seoul')::date AS claim_date,
                       coalesce(qa.severity, 0) AS severity
                FROM pcb.rma_claim c
                LEFT JOIN LATERAL (
                    SELECT severity FROM pcb.qa_triage triage WHERE triage.rma_id = c.rma_id
                    ORDER BY %s LIMIT 1
                ) qa ON true
                WHERE c.rma_id = :rmaId
                """.formatted(RmaHistoryOrder.TRIAGE)).param("rmaId", rmaId).query(ClaimHeader.class).optional();
    }

    public List<History> history(PageParameters page) {
        return jdbcClient.sql("SELECT " + REPORT_COLUMNS + """
                FROM app.report
                ORDER BY (reviewed_at IS NOT NULL), generated_at DESC, rma_id
                LIMIT :size OFFSET :offset
                """).param("size", page.size()).param("offset", page.offset()).query(this::historyRow).list();
    }

    public long count() {
        return jdbcClient.sql("SELECT count(*) FROM app.report").query(Long.class).single();
    }

    public Optional<Stored> find(String rmaId) {
        return read(rmaId, "");
    }

    public Optional<Stored> lock(String rmaId) {
        return read(rmaId, " FOR UPDATE");
    }

    private Optional<Stored> read(String rmaId, String lockClause) {
        return jdbcClient.sql("SELECT " + REPORT_COLUMNS +
                ", content::text AS body FROM app.report WHERE rma_id = :rmaId" + lockClause)
                .param("rmaId", rmaId).query((row, index) -> new Stored(historyRow(row, index), row.getString("body")))
                .optional();
    }

    public void create(NewReport report) {
        // 같은 RMA를 동시에 처음 생성해도 먼저 저장된 보고서를 덮어쓰지 않음
        jdbcClient.sql("""
                INSERT INTO app.report (rma_id, product, defect_type, claim_date, severity, claim_status, content, generated_at)
                VALUES (:rmaId, :product, :defectType, :claimDate, :severity, :claimStatus, CAST(:body AS jsonb), :generatedAt)
                ON CONFLICT (rma_id) DO NOTHING
                """).paramSource(report).update();
    }

    public void changeBody(String rmaId, String body, OffsetDateTime generatedAt, String defectType) {
        jdbcClient.sql("""
                UPDATE app.report SET content = CAST(:body AS jsonb),
                    defect_type = CASE WHEN CAST(:generatedAt AS timestamptz) IS NULL THEN defect_type ELSE :defectType END,
                    generated_at = coalesce(CAST(:generatedAt AS timestamptz), generated_at),
                    reviewed_by = NULL, reviewed_at = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE rma_id = :rmaId
                """).param("body", body).param("generatedAt", generatedAt).param("defectType", defectType)
                .param("rmaId", rmaId).update();
    }

    public void setReview(String rmaId, String reviewer, OffsetDateTime reviewedAt) {
        jdbcClient.sql("""
                UPDATE app.report SET reviewed_by = :reviewer, reviewed_at = :reviewedAt, updated_at = CURRENT_TIMESTAMP
                WHERE rma_id = :rmaId
                """).param("reviewer", reviewer).param("reviewedAt", reviewedAt).param("rmaId", rmaId).update();
    }

    public record ClaimHeader(String product, String claimStatus, LocalDate claimDate, int severity) {
    }

    public record History(
            String rmaId, String product, String defectType, LocalDate claimDate, String severity, String claimStatus,
            OffsetDateTime generatedAt, String reviewedBy, OffsetDateTime reviewedAt
    ) {
    }

    public record Stored(History history, String body) {
    }

    private History historyRow(ResultSet row, int index) throws SQLException {
        return new History(row.getString("rma_id"), row.getString("product"), row.getString("defect_type"),
                row.getObject("claim_date", LocalDate.class), row.getString("severity"), row.getString("claim_status"),
                row.getObject("generated_at", OffsetDateTime.class), row.getString("reviewed_by"),
                row.getObject("reviewed_at", OffsetDateTime.class));
    }

    public record NewReport(
            String rmaId, String product, String defectType, LocalDate claimDate, String severity, String claimStatus,
            String body, OffsetDateTime generatedAt
    ) {
    }

    public record Status(boolean exists, boolean reviewed) {
    }

    public Status status(String rmaId) {
        // 상세 화면은 보고서 존재 여부만 필요하므로 본문을 읽지 않음
        return jdbcClient.sql("""
                SELECT true AS exists, reviewed_at IS NOT NULL AS reviewed FROM app.report WHERE rma_id = :rmaId
                """).param("rmaId", rmaId).query(Status.class).optional().orElse(new Status(false, false));
    }
}
