package com.u1_2.effect.recurrence;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.u1_2.effect.TestcontainersConfiguration;
import com.u1_2.effect.config.jwt.JwtProvider;
import com.u1_2.effect.user.domain.Role;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = "app.clock.fixed=2025-12-31T09:00:00+09:00")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
@Sql({"/sql/recurrence-fixture.sql", "/sql/corrective-action-fixture.sql"})
class CorrectiveActionIntegrationTest {

    private static final String INVESTIGATION = "/api/recurrence/investigations/INV-A/actions";
    private static final String ACTIONS = "/api/recurrence/actions";
    private static final String PLAN = """
            {"action_role":"OCCURRENCE","action_category":"PROCESS_LOCAL","description":"공정 개선",
             "owner_dept":"QA","cause_code":"C-1","escape_code":"E-1","due_at":"2026-01-20T12:00:00+09:00",
             "scope":{"equipment":["EQ-1"],"plan":"PLAN-A"}}
            """;

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwt;
    @Autowired JsonMapper json;

    @Test
    void 조사에_한_번_등록하고_재요청과_기존_조회_API에_반영한다() throws Exception {
        String id = createId();
        create(PLAN.replace("2026-01-20T12:00:00+09:00", "2026-01-20T03:00:00Z"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.corrective_action_id").value(id));
        create(PLAN.replace("공정 개선", "다른 내용"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CORRECTIVE_ACTION_REQUEST_CONFLICT"));
        mvc.perform(quality(get(ACTIONS + "/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.action_role").value("OCCURRENCE"))
                .andExpect(jsonPath("$.content.scope.equipment[0]").value("EQ-1"))
                .andExpect(jsonPath("$.content.selected_at").value("2025-12-31T09:00:00+09:00"));
        mvc.perform(quality(get(ACTIONS)).param("investigation_id", "INV-A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.total").value(3));
    }

    @Test
    void 시행_전에는_수정하고_시행을_기록하면_계획을_잠근다() throws Exception {
        String path = ACTIONS + "/" + createId();
        mvc.perform(body(quality(put(path)), PLAN.replace("공정 개선", "담당자 계획 업데이트")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.description").value("담당자 계획 업데이트"));
        performImplementation(path, "2025-12-31T09:00:01+09:00").andExpect(status().isBadRequest());
        performImplementation(path, "2025-12-31T09:00:00+09:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.verification_status").value("MONITORING"));
        performImplementation(path, "2025-12-31T00:00:00Z").andExpect(status().isOk());
        mvc.perform(body(quality(put(path)), PLAN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CORRECTIVE_ACTION_LOCKED"));
        mvc.perform(quality(get(path + "/trend")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.completed_dt").value("2025-12-31"));
    }

    @Test
    void 이력이_있는_기존_대책을_보존하고_권한과_참조값을_검증한다() throws Exception {
        mvc.perform(quality(get(ACTIONS + "/CA-01"))).andExpect(status().isOk());
        mvc.perform(body(quality(put(ACTIONS + "/CA-01")), PLAN)).andExpect(status().isConflict());
        mvc.perform(body(quality(put(ACTIONS + "/CA-03")), PLAN)).andExpect(status().isConflict());
        mvc.perform(quality(get(ACTIONS + "/CA-01/trend")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.checks.length()").value(2));
        mvc.perform(body(quality(post(INVESTIGATION)), PLAN.replace("\"C-1\"", "\"UNKNOWN\"")))
                .andExpect(status().isBadRequest());
        mvc.perform(body(quality(post(INVESTIGATION)),
                        PLAN.replace("2026-01-20T12:00:00+09:00", "2025-12-30T00:00:00+09:00")))
                .andExpect(status().isBadRequest());
        mvc.perform(body(quality(post(INVESTIGATION.replace("INV-A", "INV-MISSING"))), PLAN))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("INVESTIGATION_NOT_FOUND"));
        mvc.perform(body(request(post(INVESTIGATION), Role.CS), PLAN)).andExpect(status().isForbidden());
    }

    private ResultActions create(String plan) throws Exception {
        return mvc.perform(body(quality(post(INVESTIGATION)).header("Idempotency-Key", "plan-key"), plan));
    }

    private String createId() throws Exception {
        var result = create(PLAN).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.verification_status").value("IMPLEMENTING")).andReturn();
        return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .at("/content/corrective_action_id").asString();
    }

    private ResultActions performImplementation(String path, String implementedAt) throws Exception {
        return mvc.perform(body(quality(post(path + "/implement")),
                "{\"implemented_at\":\"%s\"}".formatted(implementedAt)));
    }

    private MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder builder, String value) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(value);
    }

    private MockHttpServletRequestBuilder quality(MockHttpServletRequestBuilder builder) {
        return request(builder, Role.QUALITY);
    }

    private MockHttpServletRequestBuilder request(MockHttpServletRequestBuilder builder, Role role) {
        return builder.contextPath("/api")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.createAccessToken(91007L, role));
    }
}
