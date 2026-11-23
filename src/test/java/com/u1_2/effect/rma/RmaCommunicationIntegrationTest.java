package com.u1_2.effect.rma;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = {"app.clock.fixed=2025-12-31T09:00:00+09:00", "app.analysis.polling.enabled=false"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Sql(scripts = {"/sql/meta-fixture.sql", "/sql/rma-intake-fixture.sql", "/sql/rma-cause-fixture.sql",
        "/sql/rma-communication-fixture.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class RmaCommunicationIntegrationTest {

    private static final String MESSAGE = "고객 반품 건의 품질 검토 결과를 공유드립니다.";

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired JwtProvider jwt;
    @Autowired JsonMapper mapper;

    @Test
    void CS_회신은_본문과_출처를_저장하고_접수를_응답완료로_바꾼다() throws Exception {
        String id = seed("message", "TRIAGED", "L2");
        insertAnalysis(id, "ana-message");
        var response = recordMessage(id, MESSAGE, "ana-message", Role.CS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.cs_status").value("RESPONDED"))
                .andExpect(jsonPath("$.content.sent_at").value("2025-12-31T09:00:00+09:00"))
                .andReturn().getResponse();
        String messageId = mapper.readTree(response.getContentAsByteArray()).at("/content/message_id").stringValue();
        assertThat(jdbc.sql("""
                SELECT c.audience, c.author_role, c.message_text, m.source, m.analysis_id, m.author_user_id
                FROM pcb.communication c JOIN app.communication_meta m ON m.message_id = c.message_id
                WHERE c.message_id = :message
                """).param("message", messageId).query().singleRow())
                .containsEntry("audience", "customer").containsEntry("author_role", "CS")
                .containsEntry("message_text", MESSAGE).containsEntry("source", "AI_DRAFT_EDITED")
                .containsEntry("analysis_id", "ana-message").containsEntry("author_user_id", 8L);
        history(id, Role.CS).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.items[0].author_name").value("고객 대응 담당자"));
    }

    @Test
    void 회신_이력은_CS에는_고객_대화만_품질관리자에게는_내부_이력도_보여준다() throws Exception {
        String id = seed("history", "TRIAGED", "L2");
        jdbc.sql("""
                INSERT INTO pcb.communication (message_id, rma_id, audience, author_role, message_text, sent_at) VALUES
                    ('internal', :id, '설비팀', 'QA', '설비 점검 요청', '2025-12-30T09:00:00+09:00'),
                    ('incoming', :id, 'CS', 'customer', '고객 추가자료', '2025-12-30T10:00:00+09:00'),
                    ('outgoing', :id, 'customer', 'CS', '기존 고객 회신', '2025-12-30T11:00:00+09:00')
                """).param("id", id).update();
        history(id, Role.CS).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.items[*].message_id", containsInAnyOrder("incoming", "outgoing")));
        history(id, Role.QUALITY).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.items[*].message_id", containsInAnyOrder("internal", "incoming", "outgoing")));
        history("missing", Role.QUALITY).andExpect(status().isNotFound());
    }

    @Test
    void 회신_권한_본문_길이와_분석_연결을_검증하고_실패할_때_기록하지_않는다() throws Exception {
        String id = seed("validation", "TRIAGED", "L2");
        String other = seed("other", "TRIAGED", "L2");
        insertAnalysis(other, "ana-other");
        recordMessage(id, MESSAGE, null, Role.QUALITY).andExpect(status().isForbidden());
        recordMessage(id, "짧은 내용", null, Role.CS).andExpect(status().isBadRequest());
        recordMessage(id, "가".repeat(4001), null, Role.CS).andExpect(status().isBadRequest());
        recordMessage(id, MESSAGE, "ana-other", Role.CS).andExpect(status().isBadRequest());
        assertThat(jdbc.sql("SELECT COUNT(*) FROM pcb.communication WHERE rma_id = :id")
                .param("id", id).query(Long.class).single()).isZero();
    }

    @Test
    void L3의_예약은_상태를_유지하고_판정된_뒤에만_실행과_종료_시각을_저장한다() throws Exception {
        String id = seed("action", "ANALYZING", "L3");
        recordAction(id, "EXECUTED", "REPLACEMENT_SHIPMENT", 7, Role.QUALITY).andExpect(status().isForbidden());
        recordAction(id, "EXECUTED", "REPLACEMENT_SHIPMENT", 7, Role.CS)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CAUSE_NOT_DECIDED"));
        recordAction(id, "SCHEDULED", "REPLACEMENT_SHIPMENT", 7, Role.CS).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.cs_status").value("OPEN"))
                .andExpect(jsonPath("$.content.executed_at").isEmpty());

        recordMessage(id, MESSAGE, null, Role.CS).andExpect(jsonPath("$.content.cs_status").value("RESPONDED"));
        jdbc.sql("UPDATE pcb.rma_claim SET qa_status = 'CAUSE_DECIDED' WHERE rma_id = :id").param("id", id).update();
        recordAction(id, "EXECUTED", "REPLACEMENT_SHIPMENT", 7, Role.CS).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.cs_status").value("CLOSED"))
                .andExpect(jsonPath("$.content.executed_at").value("2025-12-31T09:00:00+09:00"));
        assertThat(jdbc.sql("""
                SELECT result, policy_version, executed_at, board_qty
                FROM pcb.service_action WHERE rma_item_id = 'ITEM-action' AND result = 'EXECUTED'
                """).query().singleRow()).containsEntry("policy_version", "B2B-QA-2025")
                .containsEntry("executed_at", "2025-12-31T09:00:00+09:00").containsEntry("board_qty", 7L);
        recordMessage(id, MESSAGE, null, Role.CS).andExpect(jsonPath("$.content.cs_status").value("CLOSED"));
    }

    @Test
    void 처리_수량_0은_무상_시료_회수에만_허용한다() throws Exception {
        String id = seed("quantity", "TRIAGED", "L2");
        recordAction(id, "EXECUTED", "CREDIT_NOTE", 0, Role.CS).andExpect(status().isBadRequest());
        recordAction(id, "EXECUTED", "NO_CHARGE_SAMPLE_RETURN", -1, Role.CS).andExpect(status().isBadRequest());
        recordAction(id, "EXECUTED", "NO_CHARGE_SAMPLE_RETURN", 0, Role.CS).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.cs_status").value("CLOSED"));
    }

    private ResultActions history(String rmaId, Role role) throws Exception {
        return mvc.perform(get("/api/rmas/{id}/communications", rmaId).contextPath("/api")
                .header("Authorization", token(role)));
    }

    private ResultActions recordMessage(String rmaId, String message, String analysisId, Role role) throws Exception {
        var body = mapper.createObjectNode().put("message_text", message)
                .put("source", analysisId == null ? "MANUAL" : "AI_DRAFT_EDITED");
        if (analysisId != null) {
            body.put("analysis_id", analysisId);
        }
        return mvc.perform(post("/api/rmas/{id}/communications", rmaId).contextPath("/api")
                .header("Authorization", token(role)).contentType(MediaType.APPLICATION_JSON).content(body.toString()));
    }

    private ResultActions recordAction(String rmaId, String result, String type, int qty, Role role) throws Exception {
        var body = mapper.createObjectNode().put("result", result).put("action_type", type)
                .put("board_qty", qty).put("source", "AI_SUGGESTED");
        return mvc.perform(post("/api/rmas/{id}/service-action", rmaId).contextPath("/api")
                .header("Authorization", token(role)).contentType(MediaType.APPLICATION_JSON).content(body.toString()));
    }

    private String token(Role role) {
        return "Bearer " + jwt.createAccessToken(role == Role.CS ? 8L : 7L, role);
    }

    private String seed(String suffix, String qaStatus, String level) {
        String id = "RMA-CS-" + suffix;
        jdbc.sql("""
                INSERT INTO pcb.rma_claim
                SELECT :id, :id, customer_id, opened_at, occurred_at, raw_text, reported_part_no, NULL, NULL,
                       detection_stage, 'OPEN', :qaStatus, :id
                FROM pcb.rma_claim WHERE rma_id = 'RMA-10610'
                """).param("id", id).param("qaStatus", qaStatus).update();
        jdbc.sql("""
                INSERT INTO pcb.rma_item VALUES (:item, :id, NULL, NULL, NULL, 10, 1, 0, NULL, 'UNTRACED')
                """).param("item", "ITEM-" + suffix).param("id", id).update();
        jdbc.sql("""
                INSERT INTO pcb.qa_triage VALUES (:triage, :id, 8, NULL, NULL, 'AUTOMOTIVE',
                    0, :level, '품질 판정', 'QA_AGENT', '2025-12-31T09:00:00+09:00')
                """).param("triage", "TRI-" + suffix).param("id", id).param("level", level).update();
        return id;
    }

    private void insertAnalysis(String rmaId, String analysisId) {
        jdbc.sql("""
                INSERT INTO app.ai_analysis (analysis_id, rma_id, mode, analysis_state, created_at)
                VALUES (:analysisId, :rmaId, 'B', 'SUCCEEDED', CURRENT_TIMESTAMP)
                """).param("analysisId", analysisId).param("rmaId", rmaId).update();
    }
}
