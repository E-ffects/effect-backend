package com.u1_2.effect.dashboard;

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
@Sql("/sql/cs-dashboard-fixture.sql")
class CsDashboardIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwt;

    @Test
    void 최근_30일을_기본으로_접수와_마지막_처리_실행과_손실을_집계한다() throws Exception {
        mvc.perform(request(Role.CS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.period.from").value("2025-12-02"))
                .andExpect(jsonPath("$.content.period.to").value("2025-12-31"))
                .andExpect(jsonPath("$.content.as_of").value("2025-12-31T09:00:00+09:00"))
                .andExpect(jsonPath("$.content.kpis.received_count").value(3))
                .andExpect(jsonPath("$.content.kpis.pending_count").value(3))
                .andExpect(jsonPath("$.content.kpis.closed_count").value(1))
                .andExpect(jsonPath("$.content.kpis.estimated_loss_krw").value(1500));
    }

    @Test
    void 서울_자정을_경계로_시작일과_종료일을_포함하고_처리_중은_현재_전체를_센다() throws Exception {
        mvc.perform(request(Role.CS).param("from", "2025-12-31").param("to", "2025-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.kpis.received_count").value(2))
                .andExpect(jsonPath("$.content.kpis.pending_count").value(3))
                .andExpect(jsonPath("$.content.kpis.closed_count").value(1))
                .andExpect(jsonPath("$.content.kpis.estimated_loss_krw").value(1500));
        mvc.perform(request(Role.CS).param("from", "2026-01-01").param("to", "2026-01-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.kpis.received_count").value(0))
                .andExpect(jsonPath("$.content.kpis.pending_count").value(3))
                .andExpect(jsonPath("$.content.kpis.closed_count").value(1))
                .andExpect(jsonPath("$.content.kpis.estimated_loss_krw").value(750));
        mvc.perform(request(Role.CS).param("to", "2025-11-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.period.from").value("2025-11-01"))
                .andExpect(jsonPath("$.content.kpis.pending_count").value(3))
                .andExpect(jsonPath("$.content.kpis.closed_count").value(0))
                .andExpect(jsonPath("$.content.kpis.estimated_loss_krw").value(0));
    }

    @Test
    void CS만_조회하며_잘못된_기간은_공통_입력_오류를_반환한다() throws Exception {
        mvc.perform(get("/api/dashboard/cs").contextPath("/api")).andExpect(status().isUnauthorized());
        mvc.perform(request(Role.QUALITY)).andExpect(status().isForbidden());
        mvc.perform(request(Role.CS).param("from", "2025-12-31").param("to", "2025-12-01"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mvc.perform(request(Role.CS).param("from", "2025-02-30"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    private MockHttpServletRequestBuilder request(Role role) {
        return get("/api/dashboard/cs").contextPath("/api")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.createAccessToken(1L, role));
    }
}
