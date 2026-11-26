package com.u1_2.effect.patternreport.domain;

import com.u1_2.effect.global.dto.PageParameters;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PatternReportRepository {

    private static final String HEADER = """
            id AS report_id, pattern_id, scope_from, scope_to, nullif(product, '') AS product,
            title, generated_by, generated_at
            """;

    private static final RowMapper<Header> HEADER_ROW = DataClassRowMapper.newInstance(Header.class);

    private static final RowMapper<Stored> STORED_ROW =
            (row, index) -> new Stored(HEADER_ROW.mapRow(row, index), row.getString("body"));

    private final JdbcClient jdbc;

    public Optional<Stored> find(long reportId) {
        return read("id = :reportId").param("reportId", reportId).query(STORED_ROW).optional();
    }

    public Optional<Stored> findScope(Scope scope) {
        return read("""
                pattern_id = :patternId AND scope_from = :from AND scope_to = :to AND product = :product
                """).paramSource(scope).query(STORED_ROW).optional();
    }

    public List<Header> list(String patternId, PageParameters page) {
        return history(HEADER, patternId, """
                ORDER BY generated_at DESC, id DESC LIMIT :size OFFSET :offset
                """).param("size", page.size()).param("offset", page.offset()).query(HEADER_ROW).list();
    }

    public long count(String patternId) {
        return history("count(*)", patternId, "").query(Long.class).single();
    }

    public boolean insert(Scope scope, String title, long author, OffsetDateTime now, String body) {
        // 동시 생성 시 동일 범위에서 먼저 저장된 보고서를 유지함
        return jdbc.sql("""
                INSERT INTO app.pattern_report
                    (pattern_id, scope_from, scope_to, product, title, generated_by, generated_at, content)
                VALUES (:patternId, :from, :to, :product, :title, :author, :now, CAST(:body AS jsonb))
                ON CONFLICT (pattern_id, scope_from, scope_to, product) DO NOTHING
                """).param("patternId", scope.patternId()).param("from", scope.from()).param("to", scope.to())
                .param("product", scope.product()).param("title", title).param("author", author)
                .param("now", now).param("body", body).update() == 1;
    }

    private JdbcClient.StatementSpec read(String condition) {
        return jdbc.sql("SELECT " + HEADER + ", content::text AS body FROM app.pattern_report WHERE " + condition);
    }

    private JdbcClient.StatementSpec history(String columns, String patternId, String tail) {
        return jdbc.sql("SELECT " + columns + """
                FROM app.pattern_report WHERE CAST(:patternId AS text) IS NULL OR pattern_id = :patternId
                """ + tail).param("patternId", patternId);
    }

    public record Scope(String patternId, LocalDate from, LocalDate to, String product) {
    }

    public record Header(
            long reportId, String patternId, LocalDate scopeFrom, LocalDate scopeTo, String product,
            String title, long generatedBy, OffsetDateTime generatedAt
    ) {
    }

    public record Stored(Header header, String body) {
    }
}
