package com.u1_2.effect.report;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = "app.clock.fixed=2025-12-31T09:00:00+09:00")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
@Sql("/sql/report-fixture.sql")
class ReportIntegrationTest {

    private static final String HISTORY = "/api/reports";
    private static final String REPORT = HISTORY + "/RMA-REPORT";

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwt;
    @Autowired JsonMapper json;
    @Autowired JdbcClient jdbc;

    @Test
    void 보고서는_저장된_분석과_실제_근거를_사용하고_재요청에도_한건을_유지한다() throws Exception {
        mvc.perform(quality(get(REPORT))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
        mvc.perform(quality(post(REPORT))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.claim_date").value("2025-12-10"))
                .andExpect(jsonPath("$.content.generated_at").value("2025-12-31T09:00:00+09:00"))
                .andExpect(jsonPath("$.content.product").value("PCB-A"))
                .andExpect(jsonPath("$.content.content.analysis_id").value("analysis-report-a"))
                .andExpect(jsonPath("$.content.content.evidence.length()").value(2))
                .andExpect(jsonPath("$.content.content.evidence[1]").value("RMA-EVIDENCE"))
                .andExpect(jsonPath("$.content.content.quality_recommendation").value("공정 조건 점검"));
        mvc.perform(quality(post(REPORT))).andExpect(status().isOk());
        mvc.perform(quality(get(HISTORY)).param("size", "1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.total").value(1))
                .andExpect(jsonPath("$.content.items[0].rma_id").value("RMA-REPORT"))
                .andExpect(jsonPath("$.content.items[0].content").doesNotHaveJsonPath());
    }

    @Test
    void 검토한_보고서는_본문을_수정하면_검토가_초기화된다() throws Exception {
        mvc.perform(quality(post(REPORT))).andExpect(status().isOk());
        mvc.perform(quality(post(REPORT + "/review"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.reviewed_by").value("품질 검토자"));
        mvc.perform(quality(put(REPORT)).contentType(MediaType.APPLICATION_JSON).content("""
                {"main_cause":"검토 후 원인","quality_recommendation":"개선 조치","cs_recommendation":"고객 회신"}
                """)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.reviewed").value(false))
                .andExpect(jsonPath("$.content.content.main_cause").value("검토 후 원인"))
                .andExpect(jsonPath("$.content.content.edited_by").value("품질 검토자"))
                .andExpect(jsonPath("$.content.content.evidence[1]").value("RMA-EVIDENCE"));
        mvc.perform(quality(post(REPORT))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.content.main_cause").value("검토 후 원인"));
        mvc.perform(quality(post(REPORT + "/review"))).andExpect(status().isOk());
        mvc.perform(quality(delete(REPORT + "/review"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.reviewed").value(false));
    }

    @Test
    void 비교_초안은_저장본을_바꾸지_않고_최신_초안으로만_교체한다() throws Exception {
        mvc.perform(quality(post(REPORT))).andExpect(status().isOk());
        String oldDraft = draftBody();
        jdbc.sql("""
                INSERT INTO app.ai_analysis (analysis_id, rma_id, mode, analysis_state, created_at, result_json)
                VALUES ('analysis-report-b', 'RMA-REPORT', 'B', 'PARTIAL', '2025-12-12T01:00:00+09:00',
                    '{"classification":{"minor_name":"쇼트"}}'::jsonb)
                """).update();
        String newDraft = draftBody();
        mvc.perform(quality(get(REPORT))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.content.analysis_id").value("analysis-report-a"));
        mvc.perform(quality(post(REPORT + "/replace")).contentType(MediaType.APPLICATION_JSON).content(oldDraft))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REPORT_DRAFT_STALE"));
        mvc.perform(quality(post(REPORT + "/replace")).contentType(MediaType.APPLICATION_JSON).content(newDraft))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.defect_type").value("쇼트"))
                .andExpect(jsonPath("$.content.content.analysis_id").value("analysis-report-b"))
                .andExpect(jsonPath("$.content.reviewed").value(false));
    }

    @Test
    void 분석_없는_접수와_없는_RMA와_권한_실패를_구분한다() throws Exception {
        mvc.perform(quality(post(HISTORY + "/RMA-UNANALYSED"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_ANALYSIS_NOT_READY"));
        mvc.perform(quality(post(HISTORY + "/RMA-NONE"))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RMA_NOT_FOUND"));
        mvc.perform(get(REPORT).contextPath("/api")).andExpect(status().isUnauthorized());
        mvc.perform(get(REPORT).contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.createAccessToken(91004L, Role.CS)))
                .andExpect(status().isForbidden());
    }

    private String draftBody() throws Exception {
        var result = mvc.perform(quality(post(REPORT + "/draft"))).andExpect(status().isOk()).andReturn();
        var body = json.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        return json.createObjectNode().set("content", body.at("/content/content")).toString();
    }

    private MockHttpServletRequestBuilder quality(MockHttpServletRequestBuilder request) {
        return request.contextPath("/api")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.createAccessToken(91004L, Role.QUALITY));
    }
}
