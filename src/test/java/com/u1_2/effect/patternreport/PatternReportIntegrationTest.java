package com.u1_2.effect.patternreport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = "app.clock.fixed=2025-12-31T11:00:00+09:00")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@DirtiesContext
@Sql(scripts = {"/sql/quality-dashboard-fixture.sql", "/sql/pattern-fixture.sql", "/sql/pattern-report-fixture.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class PatternReportIntegrationTest {

    private static final String CREATE = "/api/patterns/INV-A/reports";
    private static final String REPORTS = "/api/pattern-reports";
    private static final String SCOPE = """
            {"from":"2025-12-02","to":"2025-12-31"}
            """;

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwt;
    @Autowired JdbcClient jdbc;
    @Autowired JsonMapper json;

    @Test
    void 패턴_보고서는_중복_없이_저장하고_부서마다_다른_근거를_보여준다() throws Exception {
        String first = mvc.perform(authorized(post(CREATE), Role.QUALITY).contentType(MediaType.APPLICATION_JSON)
                        .content(SCOPE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.header.pattern_id").value("INV-A"))
                .andExpect(jsonPath("$.content.report.totals.claims").value(2))
                .andExpect(jsonPath("$.content.report.narrative.conclusion")
                        .value("조사에서 발생 원인으로 ‘접합 이상’ 항목을 확인했다. 조사에서 검출 누락 원인으로 ‘검사 누락’ 항목을 확인했다."))
                .andExpect(jsonPath("$.content.report.totals.lots").value(2))
                .andExpect(jsonPath("$.content.report.totals.claimed_quantity").value(36))
                .andExpect(jsonPath("$.content.report.totals.unresolved_claims").value(1))
                .andExpect(jsonPath("$.content.report.totals.investigation_loss_krw").value(1000))
                .andExpect(jsonPath("$.content.report.monthly[0].claims").value(2))
                .andExpect(jsonPath("$.content.report.process_flow[0].defect_occurrence").value(true))
                .andExpect(jsonPath("$.content.report.process_flow[1].detection_escape").value(true))
                .andExpect(jsonPath("$.content.report.evidence_cases.length()").value(2))
                .andExpect(jsonPath("$.content.report.evidence_cases[0].rma_id").value("RMA-A"))
                .andExpect(jsonPath("$.content.report.evidence_cases[0].selection_reason").value("CONFIRMED_TEST"))
                .andExpect(jsonPath("$.content.report.findings[0].summary").value("노즐 막힘 확인"))
                .andExpect(jsonPath("$.content.report.actions[0].checks.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(first).at("/content/header/report_id").asLong();
        jdbc.sql("UPDATE pcb.investigation SET title='수정된 조사명' WHERE investigation_id='INV-A'").update();

        mvc.perform(authorized(post(CREATE), Role.QUALITY).contentType(MediaType.APPLICATION_JSON).content(SCOPE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.header.report_id").value(id))
                .andExpect(jsonPath("$.content.header.title").value("납땜 불량 조사"));
        mvc.perform(authorized(get(REPORTS), Role.QUALITY).param("pattern_id", "INV-A").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.total").value(1))
                .andExpect(jsonPath("$.content.items[0].report_id").value(id));
        mvc.perform(authorized(get(REPORTS), Role.QUALITY).param("pattern_id", "INV-OTHER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.items").isEmpty());
        mvc.perform(authorized(get(REPORTS + "/" + id), Role.QUALITY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.header.title").value("납땜 불량 조사"));

        String previewPath = REPORTS + "/" + id + "/delivery-preview";
        mvc.perform(authorized(post(previewPath), Role.QUALITY).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"department":"PROCESS_ENGINEERING","request_note":"압력을 확인해 주세요.",
                                 "reply_due_date":"2026-01-02"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.department.recommended").value(true))
                .andExpect(jsonPath("$.content.request_note").value("압력을 확인해 주세요."))
                .andExpect(jsonPath("$.content.reply_due_date").value("2026-01-02"))
                .andExpect(jsonPath("$.content.actions.length()").value(1))
                .andExpect(jsonPath("$.content.evidence_cases[0].items[0].observations.length()").value(1))
                .andExpect(jsonPath("$.content.evidence_cases[0].items[0].measurements").isEmpty());

        mvc.perform(authorized(post(previewPath), Role.QUALITY).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"department\":\"QUALITY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.assessments[0].kind").value("DETECTION_ESCAPE"))
                .andExpect(jsonPath("$.content.actions").isEmpty())
                .andExpect(jsonPath("$.content.evidence_cases[0].items[0].measurements.length()").value(1))
                .andExpect(jsonPath("$.content.evidence_cases[0].items[0].observations").isEmpty());
        mvc.perform(authorized(post(previewPath), Role.QUALITY).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"department\":\"PURCHASING\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.materials[0].supplier").value("공급사 A"));
        assertThat(jdbc.sql("SELECT count(*) FROM app.pattern_report").query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void 품질_권한과_조회_범위를_확인한다() throws Exception {
        mvc.perform(get(REPORTS).contextPath("/api")).andExpect(status().isUnauthorized());
        mvc.perform(authorized(get(REPORTS), Role.CS)).andExpect(status().isForbidden());
        mvc.perform(authorized(get(REPORTS + "/99999"), Role.QUALITY))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PATTERN_REPORT_NOT_FOUND"));
        mvc.perform(authorized(post("/api/patterns/INV-EMPTY/reports"), Role.QUALITY)
                        .contentType(MediaType.APPLICATION_JSON).content(SCOPE))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PATTERN_REPORT_NO_CASES"));
        mvc.perform(authorized(post(CREATE), Role.QUALITY).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from\":\"2025-12-31\",\"to\":\"2025-12-01\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    private MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request, Role role) {
        return request.contextPath("/api").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.createAccessToken(1L, role));
    }
}
