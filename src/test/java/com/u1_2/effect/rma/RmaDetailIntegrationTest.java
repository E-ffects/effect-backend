package com.u1_2.effect.rma;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "app.clock.fixed=2025-12-31T09:00:00+09:00")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
@Sql({"/sql/meta-fixture.sql", "/sql/rma-detail-fixture.sql"})
class RmaDetailIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtProvider jwtProvider;
    @Autowired JdbcClient jdbcClient;

    @Test
    void CSV는_같은_검색과_정렬을_적용하고_페이지와_열기_옵션을_무시한다() throws Exception {
        String csv = mockMvc.perform(get("/api/rmas/export").contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(1L, Role.QUALITY))
                        .param("status", "OPEN").param("sort", "opened_at:asc").param("size", "1")
                        .param("open", "RMA-00015"))
                .andExpect(status().isOk()).andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"rma.csv\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn().getResponse().getContentAsString();
        assertThat(csv).startsWith("\uFEFFRMA ID,접수 시각,고객사")
                .contains("심각도,QA 상태,승인 상태,재발 판정\r\n")
                .contains("RMA-08927,2025-11-20T08:56:35+09:00", "RMA-10611,")
                .doesNotContain("RMA-00015");
        assertThat(csv.indexOf("RMA-08927,")).isLessThan(csv.indexOf("RMA-10611,"));
        assertThat(csv.lines()).hasSize(3);
    }

    @Test
    void CS_CSV는_품질_전용_열을_제외하고_텍스트를_안전하게_이스케이프한다() throws Exception {
        jdbcClient.sql("UPDATE pcb.customer SET customer_name = :name WHERE customer_id = 'A01'")
                .param("name", " =테스트,\"고객사\"\n두 번째 줄").update();
        var request = get("/api/rmas/export").contextPath("/api")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(1L, Role.CS))
                .param("q", "RMA-08927");
        String csv = mockMvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(csv).contains("\"' =테스트,\"\"고객사\"\"\n두 번째 줄\"")
                .doesNotContain("심각도", "QA 상태", "승인 상태", "재발 판정", "WAITING", "ANALYZING");
        mockMvc.perform(request.param("sort", "severity:desc"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void CSV는_인증이_필요하고_검색_결과가_없으면_헤더를_반환한다() throws Exception {
        mockMvc.perform(get("/api/rmas/export").contextPath("/api")).andExpect(status().isUnauthorized());
        String csv = mockMvc.perform(get("/api/rmas/export").contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(1L, Role.CS))
                        .param("q", "없는검색어"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(csv).startsWith("\uFEFFRMA ID,");
        assertThat(csv.lines()).hasSize(1);
    }

    @Test
    void 목록은_기존과_신규_접수를_최신순으로_보여주고_품목_수량을_합산한다() throws Exception {
        jdbcClient.sql("""
                INSERT INTO pcb.rma_item VALUES ('ITEM-08927-2', 'RMA-08927', NULL, NULL, 5, 4, 0, NULL)
                """).update();
        mockMvc.perform(get("/api/rmas").contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(1L, Role.QUALITY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.total").value(3))
                .andExpect(jsonPath("$.content.page").value(1))
                .andExpect(jsonPath("$.content.size").value(30))
                .andExpect(jsonPath("$.content.items[0].rma_id").value("RMA-10611"))
                .andExpect(jsonPath("$.content.items[0].intake_state").value("NEEDS_INFO"))
                .andExpect(jsonPath("$.content.items[0].analysis_state").value("NOT_REQUESTED"))
                .andExpect(jsonPath("$.content.items[1].opened_at").value("2025-11-20T08:56:35+09:00"))
                .andExpect(jsonPath("$.content.items[1].claimed_defect_qty").value(113))
                .andExpect(jsonPath("$.content.items[1].analysis_state").value("SUCCEEDED"))
                .andExpect(jsonPath("$.content.items[1].approval_status").value("WAITING"))
                .andExpect(jsonPath("$.content.items[2].recurrence_verdict").value("RECURRENCE"));
    }

    @Test
    void 목록의_open은_정렬된_페이지로_이동하며_필터로_숨긴_건도_구분한다() throws Exception {
        var request = get("/api/rmas").contextPath("/api")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(1L, Role.QUALITY))
                .param("sort", "reported_part_no:asc,opened_at:asc").param("size", "1").param("open", "RMA-10611");
        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.page").value(2))
                .andExpect(jsonPath("$.content.open_found").value(true))
                .andExpect(jsonPath("$.content.items[0].rma_id").value("RMA-10611"));
        mockMvc.perform(request.param("q", "없는검색어"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.total").value(0))
                .andExpect(jsonPath("$.content.items", hasSize(0)))
                .andExpect(jsonPath("$.content.open_found").value(false))
                .andExpect(jsonPath("$.content.open_exists").value(true));
    }

    @Test
    void 목록은_접수일과_미종료_경과일을_검증하고_CS에_내부_상태를_숨긴다() throws Exception {
        mockMvc.perform(get("/api/rmas").contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(1L, Role.CS))
                        .param("from", "2025-11-20").param("to", "2025-11-20").param("overDays", "30")
                        .param("q", "pcb-ecu6").param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.total").value(1))
                .andExpect(jsonPath("$.content.items[0].analysis_level").value("L3"))
                .andExpect(jsonPath("$.content.items[0].severity").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.content.items[0].qa_status").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.content.items[0].approval_status").doesNotHaveJsonPath());
        mockMvc.perform(get("/api/rmas").contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(1L, Role.QUALITY))
                        .param("from", "2025-12-31").param("to", "2025-11-20"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/rmas").contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(1L, Role.QUALITY))
                        .param("size", "101").param("from", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 품질관리자는_상세와_최신_분석_상태를_조회한다() throws Exception {
        detail("RMA-08927", Role.QUALITY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.rma_id").value("RMA-08927"))
                .andExpect(jsonPath("$.content.opened_at").value("2025-11-20T08:56:35+09:00"))
                .andExpect(jsonPath("$.content.customer.industry").value("AUTOMOTIVE"))
                .andExpect(jsonPath("$.content.items[0].panel_id").value("PANEL-01"))
                .andExpect(jsonPath("$.content.items[0].lot_id").value("LT-01603"))
                .andExpect(jsonPath("$.content.items[0].claimed_defect_qty").value(109))
                .andExpect(jsonPath("$.content.items[0].verified_defect_qty").value(nullValue()))
                .andExpect(jsonPath("$.content.symptom.primary_label").value("ELEC02"))
                .andExpect(jsonPath("$.content.symptom.evidence_spans[0]").value("숏트 불량이 발생했습니다"))
                .andExpect(jsonPath("$.content.triage.decided_at").value("2025-11-20T12:41:37+09:00"))
                .andExpect(jsonPath("$.content.qa_status").value("ANALYZING"))
                .andExpect(jsonPath("$.content.analysis_state").value("SUCCEEDED"))
                .andExpect(jsonPath("$.content.approval_status").value("WAITING"));
    }

    @Test
    void 고객대응관리자는_고객_통보_Lot과_분석_수준만_받는다() throws Exception {
        detail("RMA-08927", Role.CS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.reported_lot_no").value("LT-01603"))
                .andExpect(jsonPath("$.content.items[0].lot_id").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.content.triage.analysis_level").value("L3"))
                .andExpect(jsonPath("$.content.triage.severity").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.content.triage.rationale").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.content.qa_status").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.content.approval_status").doesNotHaveJsonPath());
    }

    @Test
    void 미분류_접수와_검사_결과_0건을_구분한다() throws Exception {
        detail("RMA-10611", Role.QUALITY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.intake_state").value("NEEDS_INFO"))
                .andExpect(jsonPath("$.content.qa_status").value("RECEIVED"))
                .andExpect(jsonPath("$.content.analysis_state").value("NOT_REQUESTED"))
                .andExpect(jsonPath("$.content.symptom").value(nullValue()))
                .andExpect(jsonPath("$.content.triage").value(nullValue()));

        detail("RMA-00015", Role.QUALITY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.intake_state").value("ACCEPTED"))
                .andExpect(jsonPath("$.content.items[0].verified_defect_qty").value(0));
    }

    @Test
    void 존재하지_않는_접수는_404를_반환한다() throws Exception {
        detail("RMA-99999", Role.QUALITY)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RMA_NOT_FOUND"));
    }

    private ResultActions detail(String rmaId, Role role) throws Exception {
        return mockMvc.perform(get("/api/rmas/{rmaId}", rmaId).contextPath("/api")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(1L, role)));
    }
}
