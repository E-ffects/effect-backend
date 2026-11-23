package com.u1_2.effect.rma;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.u1_2.effect.TestcontainersConfiguration;
import com.u1_2.effect.config.ai.MockAiClient;
import com.u1_2.effect.config.jwt.JwtProvider;
import com.u1_2.effect.rma.application.review.CauseRefreshService;
import com.u1_2.effect.user.domain.Role;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = {"app.analysis.polling.enabled=false", "app.case-refresh.scheduling.enabled=false"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Sql(scripts = {"/sql/meta-fixture.sql", "/sql/rma-intake-fixture.sql", "/sql/rma-cause-fixture.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class CauseDecisionIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired JwtProvider jwt;
    @Autowired CauseRefreshService refresh;
    @MockitoSpyBean MockAiClient aiClient;

    @Test
    void 승인과_확인이_원인과_조사를_연결하고_같은_원인과_설비는_조사를_재사용한다() throws Exception {
        String approved = seed("approve", "L3");
        String confirmed = seed("confirm", "L3");
        jdbc.sql("""
                INSERT INTO pcb.qa_triage VALUES ('tri-confirm', :id, 8, NULL, NULL, 'AUTOMOTIVE', 0, 'L2',
                    '품질관리자 판정', '품질 담당자', '2025-12-31T09:00:00+09:00')
                """).param("id", confirmed).update();

        decide(approved, "ana-approve", "APPROVED", "OL_ETCH_RESIDUE", Role.QUALITY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.approval_status").value("APPROVED"))
                .andExpect(jsonPath("$.content.decided_by").value("품질 담당자"));
        decide(confirmed, "ana-confirm", "CONFIRMED", "OL_ETCH_RESIDUE", Role.QUALITY)
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.approval_status").value("CONFIRMED"));

        var investigationIds = jdbc.sql("""
                SELECT DISTINCT investigation_id FROM pcb.investigation_claim WHERE rma_id IN (:ids)
                """).param("ids", List.of(approved, confirmed)).query(String.class).list();
        assertThat(investigationIds).containsExactly("INV-26-0001");
        assertThat(jdbc.sql("""
                SELECT COUNT(*) FROM pcb.claim_cause c JOIN pcb.rma_item i ON i.rma_item_id = c.rma_item_id
                WHERE applicability = 'APPLIES' AND i.rma_id IN (:ids)
                """).param("ids", List.of(approved, confirmed)).query(Long.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT qa_status FROM pcb.rma_claim WHERE rma_id = :id")
                .param("id", confirmed).query(String.class).single()).isEqualTo("CAUSE_DECIDED");

        refresh.refresh("ana-approve");
        assertThat(refreshStatus("ana-approve")).isEqualTo("DONE");
        decide(approved, "ana-approve", "APPROVED", "OL_ETCH_RESIDUE", Role.QUALITY)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CAUSE_ALREADY_DECIDED"));
    }

    @Test
    void 반려는_원본과_갱신을_건드리지_않고_권한과_최신_분석과_후보를_검증한다() throws Exception {
        String rmaId = seed("reject", "L3");
        decide(rmaId, "ana-reject", "APPROVED", "OL_ETCH_RESIDUE", Role.CS)
                .andExpect(status().isForbidden());
        decide(rmaId, "older", "APPROVED", "OL_ETCH_RESIDUE", Role.QUALITY)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ANALYSIS_MISMATCH"));
        decide(rmaId, "ana-reject", "APPROVED", "UNKNOWN", Role.QUALITY)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        decide(rmaId, "ana-reject", "CONFIRMED", "OL_ETCH_RESIDUE", Role.QUALITY)
                .andExpect(status().isBadRequest());
        decide(rmaId, "ana-reject", "REJECTED", null, Role.QUALITY)
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.approval_status").value("REJECTED"));
        assertThat(jdbc.sql("SELECT COUNT(*) FROM pcb.investigation_claim WHERE rma_id = :id")
                .param("id", rmaId).query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT refresh_status IS NULL FROM app.cause_decision WHERE rma_id = :id")
                .param("id", rmaId).query(Boolean.class).single()).isTrue();
        assertThat(jdbc.sql("SELECT qa_status FROM pcb.rma_claim WHERE rma_id = :id")
                .param("id", rmaId).query(String.class).single()).isEqualTo("ANALYZING");
    }

    @Test
    void 사례_갱신_장애는_판정을_보존하고_첫_요청_이후_최대_다섯_번_더_시도한다() throws Exception {
        String rmaId = seed("refresh", "L3");
        decide(rmaId, "ana-refresh", "APPROVED", "OL_ETCH_RESIDUE", Role.QUALITY).andExpect(status().isOk());
        doReturn(false).when(aiClient).refreshCase(any());

        refresh.refresh("ana-refresh");
        refresh.refresh("ana-refresh");
        assertThat(refreshStatus("ana-refresh")).isEqualTo("PENDING");
        assertThat(jdbc.sql("SELECT refresh_attempts FROM app.cause_decision WHERE analysis_id = 'ana-refresh'")
                .query(Integer.class).single()).isEqualTo(1);
        for (int i = 0; i < 5; i++) {
            jdbc.sql("UPDATE app.cause_decision SET next_refresh_at = CURRENT_TIMESTAMP WHERE analysis_id = 'ana-refresh'").update();
            refresh.refresh("ana-refresh");
        }
        assertThat(refreshStatus("ana-refresh")).isEqualTo("FAILED");
        assertThat(jdbc.sql("SELECT approval_status FROM app.cause_decision WHERE analysis_id = 'ana-refresh'")
                .query(String.class).single()).isEqualTo("APPROVED");
    }

    @Test
    void 사례_갱신_중에는_판정_행을_잠그지_않고_그사이_판정이_다시_저장되면_이전_결과로_덮어쓰지_않는다() throws Exception {
        String rmaId = seed("refresh-stale", "L3");
        decide(rmaId, "ana-refresh-stale", "APPROVED", "OL_ETCH_RESIDUE", Role.QUALITY).andExpect(status().isOk());
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return true;
        }).when(aiClient).refreshCase(any());

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var refreshing = executor.submit(() -> refresh.refresh("ana-refresh-stale"));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            jdbc.sql("SELECT 1 FROM app.cause_decision WHERE analysis_id = 'ana-refresh-stale' FOR UPDATE NOWAIT")
                    .query(Integer.class).single();
            // 판정을 다시 저장하면 갱신 예약이 새로 잡힘
            jdbc.sql("""
                    UPDATE app.cause_decision SET refresh_status = 'PENDING', next_refresh_at = CURRENT_TIMESTAMP
                    WHERE analysis_id = 'ana-refresh-stale'
                    """).update();
            release.countDown();
            refreshing.get();
        }
        assertThat(refreshStatus("ana-refresh-stale")).isEqualTo("PENDING");
        assertThat(jdbc.sql("SELECT refresh_attempts FROM app.cause_decision WHERE analysis_id = 'ana-refresh-stale'")
                .query(Integer.class).single()).isZero();
    }

    @Test
    void 원본_DB에_저장할_수_없으면_원인_판정과_갱신_예약도_함께_롤백한다() throws Exception {
        String rmaId = seed("rollback", "L3");
        jdbc.sql("""
                UPDATE app.ai_analysis SET result_json = '{"cause_candidates":[{"cause_code":"NO_MASTER"}]}'::jsonb
                WHERE analysis_id = 'ana-rollback'
                """).update();

        decide(rmaId, "ana-rollback", "APPROVED", "NO_MASTER", Role.QUALITY).andExpect(status().isBadRequest());
        assertThat(jdbc.sql("""
                SELECT approval_status = 'WAITING' AND refresh_status IS NULL AND next_refresh_at IS NULL
                FROM app.cause_decision WHERE analysis_id = 'ana-rollback'
                """).query(Boolean.class).single()).isTrue();
    }

    private String refreshStatus(String analysisId) {
        return jdbc.sql("SELECT refresh_status FROM app.cause_decision WHERE analysis_id = :id")
                .param("id", analysisId).query(String.class).single();
    }

    private String seed(String suffix, String level) {
        String rmaId = "RMA-TEST-" + suffix;
        jdbc.sql("""
                INSERT INTO pcb.rma_claim
                SELECT :id, :id, customer_id, opened_at, occurred_at, raw_text, reported_part_no, NULL, NULL,
                       detection_stage, 'OPEN', 'ANALYZING', :id
                FROM pcb.rma_claim WHERE rma_id = 'RMA-10610'
                """).param("id", rmaId).update();
        jdbc.sql("""
                INSERT INTO pcb.rma_item VALUES (:item, :id, NULL, NULL, NULL, 10, 1, 0, NULL, 'UNTRACED')
                """).param("item", "ITEM-" + suffix).param("id", rmaId).update();
        jdbc.sql("""
                INSERT INTO app.ai_analysis (analysis_id, rma_id, mode, analysis_state, result_json, created_at)
                VALUES (:analysis, :id, 'B', 'SUCCEEDED',
                        '{"cause_candidates":[{"cause_code":"OL_ETCH_RESIDUE","equipment_id":"OLE-02","process_id":"OL_ETCH"}]}'::jsonb,
                        CURRENT_TIMESTAMP)
                """).param("analysis", "ana-" + suffix).param("id", rmaId).update();
        jdbc.sql("""
                INSERT INTO app.cause_decision (analysis_id, rma_id, approval_status, analysis_level)
                VALUES (:analysis, :id, 'WAITING', :level)
                """).param("analysis", "ana-" + suffix).param("id", rmaId).param("level", level).update();
        return rmaId;
    }

    private ResultActions decide(String rmaId, String analysisId, String outcome, String adopted, Role role) throws Exception {
        String candidates = adopted == null ? "[]" : """
                [{"cause_code":"%s","status":"ADOPTED"}]
                """.formatted(adopted);
        return mvc.perform(post("/api/rmas/{id}/cause-decision", rmaId).contextPath("/api")
                .header("Authorization", "Bearer " + jwt.createAccessToken(7L, role))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"analysis_id":"%s","decision":"%s","candidates":%s,"comment":"판정 근거"}
                        """.formatted(analysisId, outcome, candidates)));
    }
}
