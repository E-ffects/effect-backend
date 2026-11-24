package com.u1_2.effect.dashboard;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.u1_2.effect.TestcontainersConfiguration;
import com.u1_2.effect.config.jwt.JwtProvider;
import com.u1_2.effect.user.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "app.clock.fixed=2025-12-31T09:00:00+09:00")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
@Sql("/sql/quality-dashboard-fixture.sql")
class QualityDashboardIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwt;
    @Autowired JdbcClient jdbc;

    @Test
    void 서울_기준_기간으로_KPI와_접수별_불량을_집계한다() throws Exception {
        mvc.perform(request(Role.QUALITY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.period.from").value("2025-12-02"))
                .andExpect(jsonPath("$.content.period.to").value("2025-12-31"))
                .andExpect(jsonPath("$.content.kpis.total_rma.value").value(4))
                .andExpect(jsonPath("$.content.kpis.total_rma.change_pct").value(300.0))
                .andExpect(jsonPath("$.content.kpis.loss.value").value(1250))
                .andExpect(jsonPath("$.content.kpis.loss.change_pct").value(150.0))
                .andExpect(jsonPath("$.content.kpis.repeat_patterns.value").value(1))
                .andExpect(jsonPath("$.content.kpis.repeat_patterns.new").value(1))
                .andExpect(jsonPath("$.content.kpis.recurrence_suspected.value").value(1))
                .andExpect(jsonPath("$.content.kpis.cause_confirm_rate.value").value(25.0))
                .andExpect(jsonPath("$.content.kpis.cause_confirm_rate.num").value(1))
                .andExpect(jsonPath("$.content.kpis.cause_confirm_rate.den").value(4))
                .andExpect(jsonPath("$.content.kpis.countermeasure_effect_rate.value").value(50.0))
                .andExpect(jsonPath("$.content.defect_top5[0].defect_type").value("납땜"))
                .andExpect(jsonPath("$.content.defect_top5[0].count").value(2));
    }

    @Test
    void 조사_순위는_중복_연결을_제외하고_조사_전체_손실과_근거_RMA를_반환한다() throws Exception {
        mvc.perform(request(Role.QUALITY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.priority.length()").value(2))
                .andExpect(jsonPath("$.content.repeat_rank[0].investigation_id").value("INV-A"))
                .andExpect(jsonPath("$.content.repeat_rank[0].prev_count").value(1))
                .andExpect(jsonPath("$.content.priority[0].investigation_id").value("INV-A"))
                .andExpect(jsonPath("$.content.priority[0].rma_id").value("RMA-B"))
                .andExpect(jsonPath("$.content.priority[0].evidence[1]").value("RMA-A"))
                .andExpect(jsonPath("$.content.priority[0].count").value(2))
                .andExpect(jsonPath("$.content.priority[0].defect_qty").value(20))
                .andExpect(jsonPath("$.content.priority[0].loss").value(2250))
                .andExpect(jsonPath("$.content.priority[0].score").value(100.0))
                .andExpect(jsonPath("$.content.priority[0].breakdown[1].weight").value(0.3));
    }

    @Test
    void 접수가_없는_기간은_빈_목록과_계산할_수_없는_비율을_반환한다() throws Exception {
        mvc.perform(request(Role.QUALITY).param("from", "2025-01-01").param("to", "2025-01-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.kpis.total_rma.value").value(0))
                .andExpect(jsonPath("$.content.kpis.total_rma.change_pct").value(nullValue()))
                .andExpect(jsonPath("$.content.kpis.countermeasure_effect_rate.den").value(0))
                .andExpect(jsonPath("$.content.defect_top5").isEmpty())
                .andExpect(jsonPath("$.content.repeat_rank").isEmpty())
                .andExpect(jsonPath("$.content.priority").isEmpty());
    }

    @Test
    void QUALITY만_조회할_수_있고_잘못된_기간은_공통_오류를_반환한다() throws Exception {
        mvc.perform(get("/api/dashboard/quality").contextPath("/api")).andExpect(status().isUnauthorized());
        mvc.perform(request(Role.CS)).andExpect(status().isForbidden());
        mvc.perform(request(Role.QUALITY).param("from", "2025-12-31").param("to", "2025-12-01"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 재발_판정이_확정되면_해당_시점부터_의심_KPI에서_제외한다() throws Exception {
        jdbc.sql("""
                INSERT INTO users (id, username, password, name, role, created_at, updated_at)
                VALUES (1, 'dashboard-recurrence-qa', 'unused', '담당자', 'QUALITY', LOCALTIMESTAMP, LOCALTIMESTAMP)
                """).update();
        jdbc.sql("""
                INSERT INTO app.rma_recurrence_decision (rma_id, decision, decided_by, decided_by_name, decided_at)
                VALUES ('RMA-B', 'NEW', 1, '담당자', '2026-01-01T00:00:00+09:00')
                """).update();
        mvc.perform(request(Role.QUALITY)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.kpis.recurrence_suspected.value").value(1));
        jdbc.sql("UPDATE app.rma_recurrence_decision SET decided_at = '2025-12-31T23:59:59+09:00'").update();
        mvc.perform(request(Role.QUALITY)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.kpis.recurrence_suspected.value").value(0));
    }

    private MockHttpServletRequestBuilder request(Role role) {
        return request("", role);
    }

    @Test
    void 품질_요약에_같은_기간의_접수와_원인확정과_재발의심을_표시한다() throws Exception {
        mvc.perform(request("/summary", Role.QUALITY)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.summary").value(
                        "선택 기간에 4건이 접수됐습니다. 원인 확정은 1건이고, 확인이 필요한 재발 의심은 1건입니다."));
        mvc.perform(request("/summary", Role.QUALITY).param("from", "2025-01-01").param("to", "2025-01-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.summary").value(
                        "선택 기간에 0건이 접수됐습니다. 원인 확정은 0건이고, 확인이 필요한 재발 의심은 0건입니다."));
        mvc.perform(request("/summary", Role.CS)).andExpect(status().isForbidden());
        mvc.perform(request("/summary", Role.QUALITY).param("from", "2025-12-31").param("to", "2025-12-01"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    private MockHttpServletRequestBuilder request(String suffix, Role role) {
        return get("/api/dashboard/quality" + suffix).contextPath("/api")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.createAccessToken(1L, role));
    }
}
