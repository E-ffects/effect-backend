package com.u1_2.effect.pattern;

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
@Sql({"/sql/quality-dashboard-fixture.sql", "/sql/pattern-fixture.sql"})
class PatternIntegrationTest {

    private static final String PATTERNS = "/api/patterns";

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwt;

    @Test
    void 패턴_목록은_대시보드와_같은_ID와_점수를_사용하고_원인을_접수당_한번_센다() throws Exception {
        mvc.perform(request("/api/dashboard/quality", Role.QUALITY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.priority[0].pattern_id").value("INV-A"))
                .andExpect(jsonPath("$.content.repeat_rank[0].pattern_id").value("INV-A"));
        mvc.perform(period(request(PATTERNS, Role.QUALITY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.patterns.length()").value(1))
                .andExpect(jsonPath("$.content.patterns[0].pattern_id").value("INV-A"))
                .andExpect(jsonPath("$.content.patterns[0].investigation_id").value("INV-A"))
                .andExpect(jsonPath("$.content.patterns[0].count").value(2))
                .andExpect(jsonPath("$.content.patterns[0].score").value(100.0))
                .andExpect(jsonPath("$.content.causes[0].cause").value("미확정"))
                .andExpect(jsonPath("$.content.causes[0].count").value(3))
                .andExpect(jsonPath("$.content.causes[1].count").value(1));

        mvc.perform(period(request(PATTERNS, Role.QUALITY)).param("page", "2").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.total").value(1))
                .andExpect(jsonPath("$.content.patterns").isEmpty());
        mvc.perform(period(request(PATTERNS, Role.QUALITY)).param("product", "PCB-B"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.total").value(0))
                .andExpect(jsonPath("$.content.causes[0].count").value(1));
    }

    @Test
    void 상세는_실제_연결된_RMA와_중복없는_Lot과_설비와_대책을_반환한다() throws Exception {
        mvc.perform(period(request(PATTERNS + "/INV-A", Role.QUALITY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.pattern_id").value("INV-A"))
                .andExpect(jsonPath("$.content.priority_rank").value(1))
                .andExpect(jsonPath("$.content.unresolved").value(1))
                .andExpect(jsonPath("$.content.primary_cause").value("접합 이상"))
                .andExpect(jsonPath("$.content.primary_ratio").value(100))
                .andExpect(jsonPath("$.content.secondary_ratio").value(nullValue()))
                .andExpect(jsonPath("$.content.loss").value(1000))
                .andExpect(jsonPath("$.content.evidence.length()").value(2))
                .andExpect(jsonPath("$.content.evidence[0].rma_id").value("RMA-B"))
                .andExpect(jsonPath("$.content.evidence[0].opened_at").value("2025-12-31T00:00:00+09:00"))
                .andExpect(jsonPath("$.content.evidence[0].severity").value("CRITICAL"))
                .andExpect(jsonPath("$.content.lots[0].count").value(1))
                .andExpect(jsonPath("$.content.equipments.length()").value(1))
                .andExpect(jsonPath("$.content.equipments[0].count").value(2))
                .andExpect(jsonPath("$.content.actions[0].corrective_action_id").value("ACT-A"));
    }

    @Test
    void 근거가_없는_조사와_존재하지_않는_조사를_구분한다() throws Exception {
        mvc.perform(request(PATTERNS + "/INV-EMPTY", Role.QUALITY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.priority_rank").value(nullValue()))
                .andExpect(jsonPath("$.content.evidence").isEmpty());
        mvc.perform(request(PATTERNS + "/INV-MISSING", Role.QUALITY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PATTERN_NOT_FOUND"));
    }

    @Test
    void 패턴_요약은_등록된_원인과_대책과_근거를_보여준다() throws Exception {
        String summary = "/api/dashboard/pattern-report/INV-A";
        mvc.perform(period(request(summary, Role.QUALITY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.pattern_id").value("INV-A"))
                .andExpect(jsonPath("$.content.main_cause").value("접합 이상"))
                .andExpect(jsonPath("$.content.recommendation").value("공정 조건 확인"))
                .andExpect(jsonPath("$.content.evidence[0]").value("RMA-B"));
        mvc.perform(request(summary, Role.CS)).andExpect(status().isForbidden());
    }

    @Test
    void 품질_계정만_조회하며_기간과_페이지를_검증한다() throws Exception {
        mvc.perform(get(PATTERNS).contextPath("/api")).andExpect(status().isUnauthorized());
        mvc.perform(request(PATTERNS, Role.CS)).andExpect(status().isForbidden());
        mvc.perform(request(PATTERNS + "/INV-A", Role.CS)).andExpect(status().isForbidden());
        mvc.perform(request(PATTERNS, Role.QUALITY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.period.from").value("2025-10-03"));
        mvc.perform(request(PATTERNS, Role.QUALITY).param("size", "101"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mvc.perform(request(PATTERNS, Role.QUALITY).param("from", "2024-01-01"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    private MockHttpServletRequestBuilder period(MockHttpServletRequestBuilder request) {
        return request.param("from", "2025-12-02").param("to", "2025-12-31");
    }

    private MockHttpServletRequestBuilder request(String path, Role role) {
        return get(path).contextPath("/api")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.createAccessToken(1L, role));
    }
}
