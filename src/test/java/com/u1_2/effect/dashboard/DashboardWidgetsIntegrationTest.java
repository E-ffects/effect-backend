package com.u1_2.effect.dashboard;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.MediaType;
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
class DashboardWidgetsIntegrationTest {

    private static final String LAYOUT = "/api/dashboard/layout/quality";
    private static final String TREND = "/api/dashboard/trend";
    private static final long QUALITY_USER = 91001L;
    private static final long OTHER_QUALITY_USER = 91002L;
    private static final String CUSTOM_LAYOUT = """
            {"layout":[
                {"id":"priority","width":"half","hidden":false},
                {"id":"trend","width":"full","hidden":true},
                {"id":"repeatRank","width":"half","hidden":false},
                {"id":"defectTop5","width":"quarter","hidden":false}
            ]}
            """;

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwt;
    @Autowired JdbcClient jdbcClient;

    @Test
    void 위젯의_순서와_크기와_숨김을_사용자_단위로_저장하고_다시_읽는다() throws Exception {
        jdbcClient.sql("""
                INSERT INTO users (id, username, password, name, role, created_at, updated_at)
                VALUES (91001, 'layout-a', 'unused', '품질 A', 'QUALITY', LOCALTIMESTAMP, LOCALTIMESTAMP),
                       (91002, 'layout-b', 'unused', '품질 B', 'QUALITY', LOCALTIMESTAMP, LOCALTIMESTAMP)
                """).update();
        mvc.perform(auth(get(LAYOUT), QUALITY_USER, Role.QUALITY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.layout.length()").value(4))
                .andExpect(jsonPath("$.content.layout[0].id").value("defectTop5"))
                .andExpect(jsonPath("$.content.default[0].id").value("defectTop5"));
        mvc.perform(saveLayout(CUSTOM_LAYOUT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value(nullValue()));
        mvc.perform(auth(get(LAYOUT), QUALITY_USER, Role.QUALITY))
                .andExpect(jsonPath("$.content.layout[0].id").value("priority"))
                .andExpect(jsonPath("$.content.layout[0].width").value("half"))
                .andExpect(jsonPath("$.content.layout[1].hidden").value(true))
                .andExpect(jsonPath("$.content.default[0].id").value("defectTop5"));
        mvc.perform(saveLayout(CUSTOM_LAYOUT.replace("\"hidden\":true", "\"hidden\":false")))
                .andExpect(status().isOk());
        mvc.perform(auth(get(LAYOUT), QUALITY_USER, Role.QUALITY))
                .andExpect(jsonPath("$.content.layout[1].hidden").value(false));
        mvc.perform(auth(get(LAYOUT), OTHER_QUALITY_USER, Role.QUALITY))
                .andExpect(jsonPath("$.content.layout[0].id").value("defectTop5"));
    }

    @Test
    void 저장된_배치에_신규_위젯을_기본설정으로_추가한다() throws Exception {
        jdbcClient.sql("""
                INSERT INTO users (id, username, password, name, role, created_at, updated_at)
                VALUES (91001, 'layout-a', 'unused', '품질 A', 'QUALITY', LOCALTIMESTAMP, LOCALTIMESTAMP)
                """).update();
        jdbcClient.sql("""
                INSERT INTO app.dashboard_layout (user_id, dashboard, widgets)
                VALUES (91001, 'quality', '[{"id":"priority","width":"half","hidden":true}]'::jsonb)
                """).update();

        mvc.perform(auth(get(LAYOUT), QUALITY_USER, Role.QUALITY))
                .andExpect(jsonPath("$.content.layout.length()").value(4))
                .andExpect(jsonPath("$.content.layout[0].id").value("priority"))
                .andExpect(jsonPath("$.content.layout[0].hidden").value(true))
                .andExpect(jsonPath("$.content.layout[1].id").value("defectTop5"));
    }

    @Test
    void 배치의_중복_ID와_잘못된_폭과_권한을_검증한다() throws Exception {
        mvc.perform(get(LAYOUT).contextPath("/api")).andExpect(status().isUnauthorized());
        mvc.perform(auth(get(LAYOUT), QUALITY_USER, Role.CS)).andExpect(status().isForbidden());
        mvc.perform(auth(put(LAYOUT), QUALITY_USER, Role.CS).contentType(MediaType.APPLICATION_JSON).content(CUSTOM_LAYOUT))
                .andExpect(status().isForbidden());
        mvc.perform(saveLayout(CUSTOM_LAYOUT.replace("repeatRank", "priority")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mvc.perform(saveLayout(CUSTOM_LAYOUT.replace("quarter", "thin")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 기간_시작일을_기준으로_7일씩_묶고_빈_주와_서울_시행일을_반환한다() throws Exception {
        mvc.perform(auth(get(TREND), QUALITY_USER, Role.QUALITY)
                        .param("from", "2025-12-01").param("to", "2025-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.points.length()").value(5))
                .andExpect(jsonPath("$.content.points[0].label").value("12/1"))
                .andExpect(jsonPath("$.content.points[0].count").value(2))
                .andExpect(jsonPath("$.content.points[1].count").value(0))
                .andExpect(jsonPath("$.content.points[4].to").value("2025-12-31"))
                .andExpect(jsonPath("$.content.points[4].count").value(3))
                .andExpect(jsonPath("$.content.countermeasures[0].date").value("2025-12-03"))
                .andExpect(jsonPath("$.content.countermeasures[0].label").value("ACT-A PROCESS_CONTROL"));
    }

    @Test
    void 대책은_예전_접수와_연결되더라도_같은_제품과_유형이면_시행일에_표시한다() throws Exception {
        var query = auth(get(TREND), QUALITY_USER, Role.QUALITY)
                .param("from", "2025-12-03").param("to", "2025-12-09")
                .param("product", "PCB-A").param("defectType", "납땜");
        mvc.perform(query)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.points[0].count").value(0))
                .andExpect(jsonPath("$.content.countermeasures.length()").value(1));

        mvc.perform(auth(get(TREND), QUALITY_USER, Role.QUALITY)
                        .param("product", "PCB-A").param("defectType", "전기"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.points[0].count").value(0))
                .andExpect(jsonPath("$.content.points[4].count").value(0))
                .andExpect(jsonPath("$.content.countermeasures").isEmpty());
    }

    @Test
    void CS에는_공통_접수_추이만_제공하고_비로그인과_너무_긴_기간은_거부한다() throws Exception {
        mvc.perform(auth(get(TREND), QUALITY_USER, Role.CS)
                        .param("product", "PCB-A").param("defectType", "납땜"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.points[0].count").value(1))
                .andExpect(jsonPath("$.content.points[4].count").value(1))
                .andExpect(jsonPath("$.content.countermeasures").isEmpty());
        mvc.perform(get(TREND).contextPath("/api")).andExpect(status().isUnauthorized());
        mvc.perform(auth(get(TREND), QUALITY_USER, Role.QUALITY).param("from", "2024-01-01").param("to", "2025-12-31"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    private MockHttpServletRequestBuilder saveLayout(String content) {
        return auth(put(LAYOUT), QUALITY_USER, Role.QUALITY).contentType(MediaType.APPLICATION_JSON).content(content);
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, long userId, Role role) {
        return request.contextPath("/api").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.createAccessToken(userId, role));
    }
}
