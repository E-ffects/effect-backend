package com.u1_2.effect.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.u1_2.effect.TestcontainersConfiguration;
import com.u1_2.effect.config.ai.AiClient.ChatEvidence;
import com.u1_2.effect.config.ai.AiClient.ChatReply;
import com.u1_2.effect.config.ai.AiClient.ChatRequest;
import com.u1_2.effect.config.ai.MockAiClient;
import com.u1_2.effect.config.jwt.JwtProvider;
import com.u1_2.effect.user.domain.Role;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = {"app.clock.fixed=2025-12-31T09:00:00+09:00", "app.analysis.polling.enabled=false",
        "app.case-refresh.scheduling.enabled=false", "app.chat.rate-limit-per-minute=5"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Sql(scripts = {"/sql/meta-fixture.sql", "/sql/rma-detail-fixture.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class ChatIntegrationTest {

    private static final long QUALITY_USER = 92001L;
    private static final long CS_USER = 92002L;
    private static final long OTHER_QUALITY_USER = 92003L;
    private static final String ANALYZED = "RMA-08927";
    private static final String NOT_ANALYZED = "RMA-10611";

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired JwtProvider jwt;
    @Autowired JsonMapper mapper;
    @Autowired StringRedisTemplate redis;
    @MockitoSpyBean MockAiClient aiClient;

    @BeforeEach
    void reset() {
        jdbc.sql("""
                INSERT INTO users (id, username, password, name, role, created_at, updated_at) VALUES
                    (92001, 'chat-quality', 'unused', '품질', 'QUALITY', LOCALTIMESTAMP, LOCALTIMESTAMP),
                    (92002, 'chat-cs', 'unused', '고객대응', 'CS', LOCALTIMESTAMP, LOCALTIMESTAMP),
                    (92003, 'chat-quality-2', 'unused', '품질2', 'QUALITY', LOCALTIMESTAMP, LOCALTIMESTAMP)
                ON CONFLICT (id) DO NOTHING
                """).update();
        jdbc.sql("DELETE FROM app.chat_thread").update();
        jdbc.sql("""
                UPDATE app.ai_analysis SET result_json = CAST(:result AS jsonb) WHERE analysis_id = 'ana-latest'
                """).param("result", """
                {"classification":{"minor_name":"쇼트"},
                 "cause_candidates":[{"rank":1,"cause_code":"OL_ETCH_RESIDUE","equipment_id":"OLE-02"}],
                 "qa_review":{"analysis_level":"L3","needs_approval":true,"review_note":"내부 검토 메모"},
                 "drafts":{"customer_reply":"회신 초안"}}
                """).update();
        var keys = redis.keys("chat:rate:*");
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @Test
    void 같은_대화로_이어_물으면_이전_메시지를_보내고_질문과_답변을_순서대로_저장한다() throws Exception {
        String threadId = threadId(chat(QUALITY_USER, Role.QUALITY, "RMA", ANALYZED, null, "이 건 원인이 뭐야?")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.status").value("ANSWERED"))
                .andExpect(jsonPath("$.content.answer", containsString("이전 대화 0개"))));

        chat(QUALITY_USER, Role.QUALITY, "RMA", ANALYZED, threadId, "2순위는?")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.thread_id").value(threadId))
                .andExpect(jsonPath("$.content.answer", containsString("이전 대화 2개")));

        var saved = jdbc.sql("""
                SELECT m.role || ':' || COALESCE(m.status, '-') || ':' || m.text
                FROM app.chat_message m JOIN app.chat_thread t USING (thread_id)
                WHERE t.thread_id = CAST(:id AS uuid) AND t.scope_type = 'RMA' AND t.scope_id = :rma ORDER BY m.message_id
                """).param("id", threadId).param("rma", ANALYZED).query(String.class).list();
        assertThat(saved).hasSize(4);
        assertThat(saved.get(0)).isEqualTo("user:-:이 건 원인이 뭐야?");
        assertThat(saved.get(1)).startsWith("assistant:ANSWERED:");
        assertThat(saved.get(2)).isEqualTo("user:-:2순위는?");
    }

    @Test
    void 새로고침하면_이_사용자의_열린_대화를_순서대로_돌려주고_없으면_null이다() throws Exception {
        String first = threadId(chat(QUALITY_USER, Role.QUALITY, "RMA", ANALYZED, null, "이전 대화"));
        String threadId = threadId(chat(QUALITY_USER, Role.QUALITY, "RMA", ANALYZED, null, "이 건 원인이 뭐야?"));
        chat(QUALITY_USER, Role.QUALITY, "RMA", ANALYZED, threadId, "2순위는?").andExpect(status().isOk());
        assertThat(threadId).isNotEqualTo(first);

        thread(QUALITY_USER, Role.QUALITY, "RMA", ANALYZED)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.thread_id").value(threadId))
                .andExpect(jsonPath("$.content.scope_type").value("RMA"))
                .andExpect(jsonPath("$.content.messages.length()").value(4))
                .andExpect(jsonPath("$.content.messages[0].role").value("user"))
                .andExpect(jsonPath("$.content.messages[0].text").value("이 건 원인이 뭐야?"))
                .andExpect(jsonPath("$.content.messages[0].status").doesNotExist())
                .andExpect(jsonPath("$.content.messages[1].role").value("assistant"))
                .andExpect(jsonPath("$.content.messages[1].status").value("ANSWERED"))
                .andExpect(jsonPath("$.content.messages[1].evidence").isArray())
                .andExpect(jsonPath("$.content.messages[3].created_at").exists());

        thread(OTHER_QUALITY_USER, Role.QUALITY, "RMA", ANALYZED)
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").doesNotExist());
        thread(QUALITY_USER, Role.QUALITY, "RMA", NOT_ANALYZED)
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").doesNotExist());
        thread(QUALITY_USER, Role.QUALITY, "UNKNOWN", ANALYZED).andExpect(status().isBadRequest());
    }

    @Test
    void thread_id_없이_보내면_이전_대화를_닫고_닫힌_대화나_다른_사용자의_대화로는_이어갈_수_없다() throws Exception {
        String first = threadId(chat(QUALITY_USER, Role.QUALITY, "RMA", ANALYZED, null, "첫 질문"));
        String second = threadId(chat(QUALITY_USER, Role.QUALITY, "RMA", ANALYZED, null, "새 대화 질문"));
        assertThat(second).isNotEqualTo(first);
        assertThat(jdbc.sql("""
                SELECT thread_id::text FROM app.chat_thread
                WHERE user_id = :user AND scope_id = :rma AND closed_at IS NULL
                """).param("user", QUALITY_USER).param("rma", ANALYZED).query(String.class).list())
                .containsExactly(second);

        chat(QUALITY_USER, Role.QUALITY, "RMA", ANALYZED, first, "닫힌 대화")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CHAT_THREAD_NOT_FOUND"));
        chat(OTHER_QUALITY_USER, Role.QUALITY, "RMA", ANALYZED, second, "남의 대화")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CHAT_THREAD_NOT_FOUND"));
        // 같은 대화를 다른 RMA 화면에서 쓰면 문맥이 섞이므로 막음
        chat(QUALITY_USER, Role.QUALITY, "RMA", NOT_ANALYZED, second, "다른 RMA")
                .andExpect(status().isNotFound());
    }

    @Test
    void CS에는_화면과_같은_필터를_적용한_문맥을_보내고_원인_근거는_응답에서도_뺀다() throws Exception {
        doReturn(new ChatReply("ANSWERED", "REPLY", "회신 초안을 확인해 주세요.",
                List.of(new ChatEvidence("ANALYSIS_CANDIDATE", null, "1순위 외층 에칭 잔동"),
                        new ChatEvidence("SIMILAR_CASE", "RMA-00015", "과거 처리 사례")),
                List.of(), List.of(), "mock-chat", null)).when(aiClient).chat(any());

        chat(CS_USER, Role.CS, "RMA", ANALYZED, null, "고객한테 뭐라고 회신해?")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.evidence.length()").value(1))
                .andExpect(jsonPath("$.content.evidence[0].type").value("SIMILAR_CASE"));

        var captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(aiClient).chat(captor.capture());
        var sent = captor.getValue();
        assertThat(sent.audience()).isEqualTo("CS");
        JsonNode context = sent.context();
        assertThat(context.path("analysis").path("result").has("cause_candidates")).isFalse();
        assertThat(context.path("analysis").path("result").path("drafts").path("customer_reply").stringValue())
                .isEqualTo("회신 초안");
        assertThat(context.path("rma").path("items").path(0).has("lot_id")).isFalse();
        assertThat(context.toString()).doesNotContain("OLE-02", "내부 검토 메모");
    }

    @Test
    void 분석이_없으면_분석_시작을_제안하고_CS는_패턴_대화를_시작할_수_없다() throws Exception {
        chat(QUALITY_USER, Role.QUALITY, "RMA", NOT_ANALYZED, null, "원인이 뭐야?")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.status").value("NEED_ANALYSIS"))
                .andExpect(jsonPath("$.content.suggestions[0].task").value("start_analysis"));
        chat(CS_USER, Role.CS, "PATTERN", "INV-A", null, "주요 원인은?")
                .andExpect(status().isForbidden());
        chat(QUALITY_USER, Role.QUALITY, "RMA", "RMA-MISSING", null, "원인은?")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RMA_NOT_FOUND"));
        assertThat(jdbc.sql("SELECT count(*) FROM app.chat_thread WHERE scope_id IN ('INV-A', 'RMA-MISSING')")
                .query(Long.class).single()).isZero();
    }

    @Test
    void AI_장애면_아무것도_저장하지_않고_요청_한도를_넘으면_429를_준다() throws Exception {
        chat(QUALITY_USER, Role.QUALITY, "RMA", ANALYZED, null, "장애 상황 질문")
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"));
        assertThat(jdbc.sql("SELECT count(*) FROM app.chat_thread").query(Long.class).single()).isZero();

        // 고정 시계라 모든 요청이 같은 1분 창에 들어감. 장애 요청 1번 포함 5번까지 허용
        for (int i = 0; i < 4; i++) {
            chat(QUALITY_USER, Role.QUALITY, "RMA", ANALYZED, null, "질문 " + i).andExpect(status().isOk());
        }
        chat(QUALITY_USER, Role.QUALITY, "RMA", ANALYZED, null, "한도 초과")
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("CHAT_RATE_LIMITED"));
        chat(OTHER_QUALITY_USER, Role.QUALITY, "RMA", ANALYZED, null, "다른 사용자").andExpect(status().isOk());
    }

    @Test
    void 메시지와_범위가_없거나_형식이_틀리면_400이다() throws Exception {
        chat(QUALITY_USER, Role.QUALITY, "RMA", ANALYZED, null, " ").andExpect(status().isBadRequest());
        chat(QUALITY_USER, Role.QUALITY, "RMA", ANALYZED, null, "가".repeat(1001)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/chat").contextPath("/api").header("Authorization", token(QUALITY_USER, Role.QUALITY))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"질문\"}"))
                .andExpect(status().isBadRequest());
    }

    private ResultActions chat(long userId, Role role, String type, String id, String threadId, String message)
            throws Exception {
        var body = mapper.createObjectNode().put("message", message);
        body.putObject("scope").put("type", type).put("id", id);
        if (threadId != null) {
            body.put("thread_id", threadId);
        }
        return mvc.perform(post("/api/chat").contextPath("/api").header("Authorization", token(userId, role))
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()));
    }

    private ResultActions thread(long userId, Role role, String type, String id) throws Exception {
        return mvc.perform(get("/api/chat/threads").contextPath("/api").header("Authorization", token(userId, role))
                .param("scope_type", type).param("scope_id", id));
    }

    private String threadId(ResultActions result) throws Exception {
        var body = mapper.readTree(result.andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        return body.path("content").path("thread_id").stringValue();
    }

    private String token(long userId, Role role) {
        return "Bearer " + jwt.createAccessToken(userId, role);
    }
}
