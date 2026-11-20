package com.u1_2.effect.rma;

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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
@Sql({"/sql/meta-fixture.sql", "/sql/rma-detail-fixture.sql"})
class RmaDetailIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtProvider jwtProvider;

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
