package com.u1_2.effect.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.u1_2.effect.TestcontainersConfiguration;
import com.u1_2.effect.config.ai.AiClient.ChatRequest;
import com.u1_2.effect.config.ai.MockAiClient;
import com.u1_2.effect.config.jwt.JwtProvider;
import com.u1_2.effect.user.domain.Role;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = {"app.clock.fixed=2025-12-31T09:00:00+09:00", "app.chat.rate-limit-per-minute=20"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Sql(scripts = {"/sql/quality-dashboard-fixture.sql", "/sql/pattern-fixture.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class ChatPatternIntegrationTest {

    private static final long QUALITY_USER = 92101L;

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired JwtProvider jwt;
    @Autowired JsonMapper mapper;
    @MockitoSpyBean MockAiClient aiClient;

    @Test
    void 패턴_대화는_화면과_같은_기간의_집계를_문맥으로_보내고_패턴별로_나뉜다() throws Exception {
        jdbc.sql("""
                INSERT INTO users (id, username, password, name, role, created_at, updated_at)
                VALUES (92101, 'chat-pattern', 'unused', '품질', 'QUALITY', LOCALTIMESTAMP, LOCALTIMESTAMP)
                ON CONFLICT (id) DO NOTHING
                """).update();
        var body = mapper.createObjectNode().put("message", "이 패턴 주요 원인이 뭐야?");
        body.putObject("scope").put("type", "PATTERN").put("id", "INV-A").put("from", "2025-12-02").put("to", "2025-12-31");
        String token = "Bearer " + jwt.createAccessToken(QUALITY_USER, Role.QUALITY);

        mvc.perform(post("/api/chat").contextPath("/api").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.status").value("ANSWERED"))
                .andExpect(jsonPath("$.content.evidence[0].type").value("RMA"));

        var captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(aiClient).chat(captor.capture());
        var context = captor.getValue().context();
        assertThat(captor.getValue().scope().type()).isEqualTo("PATTERN");
        assertThat(context.path("pattern").path("investigation_id").stringValue()).isEqualTo("INV-A");
        assertThat(context.path("pattern").path("evidence").isArray()).isTrue();
        assertThat(context.path("report").path("title").stringValue()).isEqualTo("납땜 불량 조사");
        assertThat(context.path("period").path("from").stringValue()).isEqualTo("2025-12-02");

        assertThat(jdbc.sql("""
                SELECT count(*) FROM app.chat_message m JOIN app.chat_thread t USING (thread_id)
                WHERE t.user_id = :user AND t.scope_type = 'PATTERN' AND t.scope_id = 'INV-A'
                """).param("user", QUALITY_USER).query(Long.class).single()).isEqualTo(2);

        body.putObject("scope").put("type", "PATTERN").put("id", "INV-MISSING");
        mvc.perform(post("/api/chat").contextPath("/api").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PATTERN_NOT_FOUND"));
    }
}
