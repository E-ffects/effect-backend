package com.u1_2.effect.recurrence;

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
@Sql("/sql/recurrence-fixture.sql")
class RecurrenceIntegrationTest {

    private static final String ACTIONS = "/api/recurrence/actions";
    private static final String REVIEWS = "/api/recurrence/reviews";

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwt;

    @Test
    void 조사별_대책은_최신_판정으로_확정_재발_접수를_중복없이_센다() throws Exception {
        mvc.perform(quality(ACTIONS).param("investigation_id", "INV-A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.total").value(2))
                .andExpect(jsonPath("$.content.items[0].corrective_action_id").value("CA-02"))
                .andExpect(jsonPath("$.content.items[0].verification_status").value("IMPLEMENTING"))
                .andExpect(jsonPath("$.content.items[1].corrective_action_id").value("CA-01"))
                .andExpect(jsonPath("$.content.items[1].implemented_at").value("2025-12-10T00:00:00+09:00"))
                .andExpect(jsonPath("$.content.items[1].recurrence_count").value(2));

        mvc.perform(quality(ACTIONS).param("investigation_id", "INV-A").param("page", "2").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.total").value(2))
                .andExpect(jsonPath("$.content.items.length()").value(1))
                .andExpect(jsonPath("$.content.items[0].corrective_action_id").value("CA-01"));
    }

    @Test
    void 시행일로_주간_전후를_나누고_다른_조사의_확정_재발과_검증_지표를_반환한다() throws Exception {
        mvc.perform(quality(ACTIONS + "/CA-01/trend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.completed_dt").value("2025-12-10"))
                .andExpect(jsonPath("$.content.points.length()").value(12))
                .andExpect(jsonPath("$.content.points[0].count").value(0))
                .andExpect(jsonPath("$.content.points[7].count").value(1))
                .andExpect(jsonPath("$.content.points[7].recurrence").value(false))
                .andExpect(jsonPath("$.content.points[8].from").value("2025-12-10"))
                .andExpect(jsonPath("$.content.points[8].count").value(1))
                .andExpect(jsonPath("$.content.points[8].after").value(true))
                .andExpect(jsonPath("$.content.points[9].recurrence").value(true))
                .andExpect(jsonPath("$.content.points[9].count").value(1))
                .andExpect(jsonPath("$.content.before_avg").value(0.13))
                .andExpect(jsonPath("$.content.after_avg").value(0.5))
                .andExpect(jsonPath("$.content.recent_trend").value("감소"))
                .andExpect(jsonPath("$.content.checks[0].metric").value("동일 원인 접수 수"))
                .andExpect(jsonPath("$.content.checks[0].post_value").value(2.0))
                .andExpect(jsonPath("$.content.checks[0].window_from").value("2025-12-10T00:00:00+09:00"))
                .andExpect(jsonPath("$.content.checks[1].post_value").value(nullValue()));
    }

    @Test
    void 미시행_대책은_최근_8주만_내려주고_이력은_정정_기록까지_페이지로_조회한다() throws Exception {
        mvc.perform(quality(ACTIONS + "/CA-02/trend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.completed_dt").value(nullValue()))
                .andExpect(jsonPath("$.content.points.length()").value(8))
                .andExpect(jsonPath("$.content.after_avg").value(nullValue()))
                .andExpect(jsonPath("$.content.checks").isEmpty());

        mvc.perform(quality(REVIEWS).param("corrective_action_id", "CA-01").param("verdict", "RECURRENCE")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.total").value(4))
                .andExpect(jsonPath("$.content.items[0].rma_id").value("RMA-OUTSIDE"))
                .andExpect(jsonPath("$.content.items[0].lot_produced_after_action").value(true));
        mvc.perform(quality(REVIEWS).param("corrective_action_id", "CA-01").param("verdict", "PRE_ACTION_PRODUCT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.total").value(1))
                .andExpect(jsonPath("$.content.items[0].lot_produced_after_action").value(false));
        mvc.perform(quality(REVIEWS).param("verdict", "PENDING_CAUSE_REVIEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.items[0].cause_match").value(false));
    }

    @Test
    void 품질_권한과_존재하지_않는_대책과_페이지_오류를_검증한다() throws Exception {
        mvc.perform(get(ACTIONS).contextPath("/api")).andExpect(status().isUnauthorized());
        for (String path : new String[]{ACTIONS, ACTIONS + "/CA-01/trend", REVIEWS}) {
            mvc.perform(request(path, Role.CS)).andExpect(status().isForbidden());
        }
        mvc.perform(quality(ACTIONS + "/CA-MISSING/trend"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CORRECTIVE_ACTION_NOT_FOUND"));
        mvc.perform(quality(REVIEWS).param("corrective_action_id", "CA-MISSING"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CORRECTIVE_ACTION_NOT_FOUND"));
        mvc.perform(quality(REVIEWS).param("size", "101"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    private MockHttpServletRequestBuilder quality(String path) {
        return request(path, Role.QUALITY);
    }

    private MockHttpServletRequestBuilder request(String path, Role role) {
        return get(path).contextPath("/api")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.createAccessToken(1L, role));
    }
}
