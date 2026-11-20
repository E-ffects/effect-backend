package com.u1_2.effect.meta;

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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
@Sql("/sql/meta-fixture.sql")
class MetaIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtProvider jwtProvider;

    @Test
    void 로그인하면_PCB_기준_정보를_조회한다() throws Exception {
        String token = jwtProvider.createAccessToken(1L, Role.CS);

        mockMvc.perform(get("/api/meta").contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.content.customers[0].customer_id").value("A01"))
                .andExpect(jsonPath("$.content.customers[0].customer_name").value("고객사 A01"))
                .andExpect(jsonPath("$.content.customers[1].customer_id").value("A02"))
                .andExpect(jsonPath("$.content.products[0].spec_id").value("SPEC-001"))
                .andExpect(jsonPath("$.content.products[0].part_no").value("PCB-ECU6-A1"))
                .andExpect(jsonPath("$.content.products[0].product_family").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.content.symptoms").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.content.detection_stages.length()").value(5))
                .andExpect(jsonPath("$.content.detection_stages[0].code").value("CUSTOMER_IQC"))
                .andExpect(jsonPath("$.content.detection_stages[0].label").value("고객 수입검사"))
                .andExpect(jsonPath("$.content.customer_requests.length()").value(3))
                .andExpect(jsonPath("$.content.customer_requests[0]").value("대체품 출하"))
                .andExpect(jsonPath("$.content.customer_requests[1]").value("대금 공제"))
                .andExpect(jsonPath("$.content.customer_requests[2]").value("선별 지원·대금 공제"))
                .andExpect(jsonPath("$.content.reference_date").value("2025-12-31"));
    }

    @Test
    void 토큰이_없으면_기준_정보를_조회할_수_없다() throws Exception {
        mockMvc.perform(get("/api/meta").contextPath("/api"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
