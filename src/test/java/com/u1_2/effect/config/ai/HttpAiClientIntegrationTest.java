package com.u1_2.effect.config.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpServer;
import com.u1_2.effect.TestcontainersConfiguration;
import com.u1_2.effect.config.ai.AiClient.AnalysisRequest;
import com.u1_2.effect.config.ai.AiClient.CaseRefreshRequest;
import com.u1_2.effect.config.jwt.JwtProperties;
import com.u1_2.effect.config.jwt.JwtProvider;
import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.global.exception.CommonErrorCode;
import com.u1_2.effect.global.exception.ErrorCode;
import com.u1_2.effect.rma.domain.RmaErrorCode;
import com.u1_2.effect.user.domain.Role;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = {"ai.mode=http", "app.clock.fixed=2025-12-31T09:00:00+09:00",
        "app.analysis.polling.enabled=false", "app.case-refresh.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Sql(scripts = {"/sql/meta-fixture.sql", "/sql/rma-intake-fixture.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class HttpAiClientIntegrationTest {

    private static final List<Call> CALLS = new CopyOnWriteArrayList<>();
    private static volatile Function<Call, Reply> responder = call -> new Reply(500, "{}");
    private static final HttpServer SERVER = startServer();

    @TempDir static Path storageDirectory;

    @Autowired AiClient aiClient;
    @Autowired MockMvc mvc;
    @Autowired JsonMapper jsonMapper;
    @Autowired JwtProvider jwt;
    @Autowired JwtProperties jwtProperties;
    @Autowired JdbcClient jdbc;

    @DynamicPropertySource
    static void aiAddress(DynamicPropertyRegistry registry) {
        registry.add("ai.base-url", () -> "http://127.0.0.1:" + SERVER.getAddress().getPort() + "/ai/v1");
        registry.add("app.attachments.directory", () -> storageDirectory.toString());
    }

    @BeforeEach
    void resetCalls() {
        CALLS.clear();
    }

    @AfterAll
    static void stopServer() {
        SERVER.stop(0);
    }

    @Test
    void http_모드의_접수는_실제_HTTP로_분류_QA_분석을_순서대로_등록한다() throws Exception {
        responder = call -> switch (call.path()) {
            case "/ai/v1/classify" -> new Reply(200, """
                    {"status":"CLASSIFIED","major_name":"전기적 불량","minor_code":"ELEC02","minor_name":"쇼트",
                     "evidence_spans":[],"follow_up_questions":[]}
                    """);
            case "/ai/v1/triage" -> new Reply(200, """
                    {"severity":8,"customer_industry":"AUTOMOTIVE","repeat_signal":0,"analysis_level":"L3",
                     "needs_approval":true,"approval_reasons":[],"provisional":false,"rationale":"품질 확인",
                     "as_of":"2025-12-31T09:00:00+09:00"}
                    """);
            case "/ai/v1/analyses" -> new Reply(202, """
                    {"analysis_id":"ana-http-intake","status":"QUEUED","created_at":"2026-11-20T10:00:00+09:00",
                     "poll_after_ms":2000}
                    """);
            default -> new Reply(404, "{}");
        };

        var result = mvc.perform(multipart("/api/intake").contextPath("/api")
                        .file(new MockMultipartFile("raw_file", "claim.txt", "text/plain",
                                "ICT 검사에서 쇼트 발견".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + jwt.createAccessToken(7L, Role.CS))
                        .param("customer_id", "A01").param("reported_part_no", "PCB-ECU6-A1")
                        .param("occurred_at", "2025-12-30").param("detection_stage", "ICT_FUNCTION")
                        .param("claimed_defect_qty", "1").param("input_qty", "10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.intake_state").value("ACCEPTED"))
                .andExpect(jsonPath("$.content.analysis.analysis_id").value("ana-http-intake"))
                .andReturn();

        String rmaId = jsonMapper.readTree(result.getResponse().getContentAsByteArray()).at("/content/rma_id").stringValue();
        assertThat(aiClient).isInstanceOf(HttpAiClient.class);
        assertThat(CALLS).extracting(Call::path).containsExactly("/ai/v1/classify", "/ai/v1/triage", "/ai/v1/analyses");
        assertThat(jsonMapper.readTree(CALLS.get(0).body()).path("raw_text").stringValue()).contains("쇼트");
        assertThat(jsonMapper.readTree(CALLS.get(1).body())).isEqualTo(jsonMapper.createObjectNode().put("rma_id", rmaId));
        JsonNode analysis = jsonMapper.readTree(CALLS.get(2).body());
        assertThat(analysis.properties()).extracting(Map.Entry::getKey).containsExactlyInAnyOrder("rma_id", "options");
        assertThat(analysis.at("/options/include_drafts").asBoolean()).isTrue();
        assertThat(analysis.at("/options/top_k").asInt()).isEqualTo(3);

        var key = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
        String serviceToken = CALLS.getFirst().authorization().substring("Bearer ".length());
        var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(serviceToken).getPayload();
        assertThat(claims.getSubject()).isEqualTo("spring");
        assertThat(claims.get("role")).isEqualTo("SYSTEM");
        assertThat(claims.get("type")).isEqualTo("ACCESS");
        assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime()).isEqualTo(600_000L);
        mvc.perform(get("/api/auth/me").contextPath("/api").header("Authorization", "Bearer " + serviceToken))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.sql("SELECT poll_interval_ms FROM app.ai_analysis WHERE analysis_id = 'ana-http-intake'")
                .query(Integer.class).single()).isEqualTo(2000);
        assertThat(jdbc.sql("""
                SELECT r.opened_at = s.created_at AND r.opened_at = t.decided_at
                FROM pcb.rma_claim r JOIN pcb.rma_item i USING (rma_id)
                JOIN pcb.symptom_assessment s USING (rma_item_id) JOIN pcb.qa_triage t USING (rma_id)
                WHERE r.rma_id = :id
                """).param("id", rmaId).query(Boolean.class).single()).isTrue();
    }

    @Test
    void 상태_조회는_인증을_요구하고_LLM_꺼짐과_DB_장애를_구분한다() throws Exception {
        mvc.perform(get("/api/ai/health").contextPath("/api")).andExpect(status().isUnauthorized());
        var request = get("/api/ai/health").contextPath("/api")
                .header("Authorization", "Bearer " + jwt.createAccessToken(7L, Role.CS));
        responder = call -> new Reply(200, """
                {"status":"ok","db":"ok","llm":"off","version":"0.1.0","rules":"pcb-v5"}
                """);
        mvc.perform(request).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.available").value(true))
                .andExpect(jsonPath("$.content.provider").value("rules"))
                .andExpect(jsonPath("$.content.llm").value("off"));
        assertThat(CALLS).extracting(Call::path).containsExactly("/ai/v1/health");

        responder = call -> new Reply(200, """
                {"status":"degraded","db":"down","llm":"ok"}
                """);
        mvc.perform(request).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.available").value(false))
                .andExpect(jsonPath("$.content.db").value("down"));
    }

    @Test
    void 유사_사례는_조회_건수를_전달하고_품질_정보를_역할별로_제한한다() throws Exception {
        responder = call -> new Reply(200, """
                {"rma_id":"RMA-10610","as_of":"2025-11-20T03:41:37Z","items":[
                  {"rma_id":"RMA-08486","opened_at":"2025-11-05T01:32:14Z","claim_date":"2025-11-05",
                   "minor_name":"쇼트","similarity":0.68,"cause_code":"CAUSE","basis":"같은 설비",
                   "equipment_id":"EQ-01","past_action":"설비를 조정함","recurrence":false,"days_to_resolve":14,
                   "snippet":"고객 원문 일부","corrective_actions":[{"category":"PROCESS","description":"조정",
                     "implemented_at":"2025-11-07T12:00:00+09:00"}]}]}
                """);
        String path = "/api/rmas/RMA-10610/similar";
        mvc.perform(get(path).contextPath("/api")
                        .header("Authorization", "Bearer " + jwt.createAccessToken(7L, Role.QUALITY))
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.as_of").value("2025-11-20T12:41:37+09:00"))
                .andExpect(jsonPath("$.content.items[0].equipment_id").value("EQ-01"))
                .andExpect(jsonPath("$.content.items[0].corrective_actions[0].description").value("조정"));
        mvc.perform(get(path).contextPath("/api")
                        .header("Authorization", "Bearer " + jwt.createAccessToken(7L, Role.CS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.items[0].similarity").value(0.68))
                .andExpect(jsonPath("$.content.items[0].days_to_resolve").value(14))
                .andExpect(jsonPath("$.content.items[0].equipment_id").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.content.items[0].basis").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.content.items[0].snippet").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.content.items[0].corrective_actions").doesNotHaveJsonPath());
        assertThat(CALLS).extracting(Call::path).containsOnly("/ai/v1/claims/RMA-10610/similar");
        assertThat(CALLS).extracting(Call::query).containsExactly("limit=1", "limit=5");
        assertThat(CALLS.getFirst().authorization()).startsWith("Bearer ");
    }

    @Test
    void 유사_사례는_없는_접수와_빈_결과와_AI_장애를_구분한다() throws Exception {
        String path = "/api/rmas/RMA-10610/similar";
        String authorization = "Bearer " + jwt.createAccessToken(7L, Role.QUALITY);
        mvc.perform(get(path).contextPath("/api")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/rmas/RMA-99999/similar").contextPath("/api").header("Authorization", authorization))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RMA_NOT_FOUND"));
        mvc.perform(get(path).contextPath("/api").header("Authorization", authorization).param("limit", "21"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        assertThat(CALLS).isEmpty();

        responder = call -> new Reply(200, """
                {"rma_id":"RMA-10610","as_of":"2025-11-20T12:41:37+09:00","items":[]}
                """);
        var authorized = get(path).contextPath("/api").header("Authorization", authorization);
        mvc.perform(authorized).andExpect(status().isOk()).andExpect(jsonPath("$.content.items").isEmpty());
        responder = call -> new Reply(200, """
                {"rma_id":"RMA-10610","as_of":"2025-11-20T12:41:37+09:00"}
                """);
        mvc.perform(authorized).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"));
        responder = call -> new Reply(422, """
                {"code":"AS_OF_INVALID","detail":"아직 QA 판정 전입니다."}
                """);
        mvc.perform(authorized).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 분석_중복과_유실을_구분하고_사례_갱신_필드를_전달한다() {
        responder = call -> switch (call.path()) {
            case "/ai/v1/analyses" -> new Reply(409, """
                    {"code":"ANALYSIS_IN_PROGRESS","analysis_id":"ana-existing"}
                    """);
            case "/ai/v1/analyses/ana-existing" -> new Reply(200, """
                    {"analysis_id":"ana-existing","status":"RUNNING"}
                    """);
            case "/ai/v1/cases/refresh" -> new Reply(200, """
                    {"rma_id":"RMA-10610","indexed":true,"case_count":10}
                    """);
            default -> new Reply(404, """
                    {"code":"ANALYSIS_NOT_FOUND"}
                    """);
        };
        var registered = aiClient.registerAnalysis(new AnalysisRequest("RMA-10610", "B", 3, true, "원문", 1));
        assertThat(registered.analysisId()).isEqualTo("ana-existing");
        assertThat(registered.pendingState()).isEqualTo("RUNNING");
        assertThat(aiClient.getAnalysis("lost")).isEmpty();
        assertThat(aiClient.refreshCase(new CaseRefreshRequest("RMA-10610", "ana-existing", "APPROVED",
                OffsetDateTime.parse("2025-12-31T09:00:00+09:00")))).isTrue();
        JsonNode refresh = jsonMapper.readTree(CALLS.getLast().body());
        assertThat(refresh.path("analysis_id").stringValue()).isEqualTo("ana-existing");
        assertThat(refresh.path("decided_at").stringValue()).isEqualTo("2025-12-31T09:00:00+09:00");
    }

    @Test
    void 채팅은_범위와_대화를_snake_case로_보내고_모르는_상태값은_AI_장애로_처리한다() {
        responder = call -> new Reply(200, """
                {"status":"ANSWERED","intent":"CAUSE","answer":"1순위 후보는 외층 에칭 잔동입니다.",
                 "evidence":[{"type":"SIMILAR_CASE","rma_id":"RMA-00015","label":"같은 증상 과거 사례"}],
                 "suggestions":[],"warnings":[],"model_version":"chat-0.1+llm"}
                """);
        var request = new AiClient.ChatRequest("thread-1", "QUALITY", new AiClient.ChatScopeRef("RMA", "RMA-10610"),
                "원인이 뭐야?", List.of(new AiClient.ChatTurn("user", "이전 질문")), jsonMapper.createObjectNode());
        var reply = aiClient.chat(request);
        assertThat(reply.evidence()).singleElement().satisfies(e -> assertThat(e.rmaId()).isEqualTo("RMA-00015"));
        assertThat(reply.modelVersion()).isEqualTo("chat-0.1+llm");
        JsonNode sent = jsonMapper.readTree(CALLS.getLast().body());
        assertThat(CALLS.getLast().path()).isEqualTo("/ai/v1/chat");
        assertThat(sent.path("thread_id").stringValue()).isEqualTo("thread-1");
        assertThat(sent.path("scope").path("type").stringValue()).isEqualTo("RMA");
        assertThat(sent.path("history").path(0).path("text").stringValue()).isEqualTo("이전 질문");

        responder = call -> new Reply(200, """
                {"status":"THINKING","answer":"..."}
                """);
        assertThatThrownBy(() -> aiClient.chat(request)).isInstanceOf(AiUnavailableException.class);
        responder = call -> new Reply(200, """
                {"status":"ANSWERED","answer":" "}
                """);
        assertThatThrownBy(() -> aiClient.chat(request)).isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void FastAPI_오류를_사용자_인증과_구분하고_v6_오류_코드로_변환한다() {
        var examples = List.of(new ErrorExample(400, "INVALID_INPUT", CommonErrorCode.INVALID_INPUT),
                new ErrorExample(404, "CLAIM_NOT_FOUND", RmaErrorCode.RMA_NOT_FOUND),
                new ErrorExample(422, "AS_OF_INVALID", CommonErrorCode.INVALID_INPUT),
                new ErrorExample(403, "FORBIDDEN", AiErrorCode.AI_UNAVAILABLE),
                new ErrorExample(502, "INTERNAL_ERROR", AiErrorCode.AI_UNAVAILABLE));
        for (var example : examples) {
            responder = call -> new Reply(example.status(), "{\"code\":\"" + example.code() + "\"}");
            assertThatThrownBy(() -> aiClient.triage("RMA-10610", "원문"))
                    .isInstanceOfSatisfying(ApiException.class,
                            error -> assertThat(error.getErrorCode()).isEqualTo(example.expected()));
        }
    }

    private static HttpServer startServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/ai/v1", exchange -> {
                try (exchange) {
                    var call = new Call(exchange.getRequestURI().getPath(),
                            exchange.getRequestURI().getQuery(),
                            exchange.getRequestHeaders().getFirst("Authorization"),
                            new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    CALLS.add(call);
                    var reply = responder.apply(call);
                    byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(reply.status(), body.length);
                    exchange.getResponseBody().write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record Call(String path, String query, String authorization, String body) {
    }

    private record Reply(int status, String body) {
    }

    private record ErrorExample(int status, String code, ErrorCode expected) {
    }
}
