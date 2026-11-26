package com.u1_2.effect.config.ai;

import com.u1_2.effect.config.jwt.JwtProvider;
import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.global.exception.CommonErrorCode;
import com.u1_2.effect.rma.domain.RmaErrorCode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@Profile({"local", "test", "prod"})
@ConditionalOnProperty(name = "ai.mode", havingValue = "http")
public class HttpAiClient implements AiClient {

    private static final Duration CLASSIFY_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);
    // 채팅은 의도 분류와 문장 작성으로 LLM을 최대 두 번 기다림
    private static final Duration CHAT_TIMEOUT = Duration.ofSeconds(20);
    private static final Set<String> CHAT_STATUSES = Set.of("ANSWERED", "NEED_ANALYSIS", "OUT_OF_SCOPE", "FALLBACK");

    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(2)).build();
    private final URI baseUrl;
    private final JsonMapper jsonMapper;
    private final JwtProvider jwtProvider;
    private final Clock appClock;

    public HttpAiClient(@Value("${ai.base-url:http://127.0.0.1:8080/ai/v1}") URI baseUrl,
                        JsonMapper jsonMapper, JwtProvider jwtProvider, Clock appClock) {
        this.baseUrl = baseUrl;
        this.jsonMapper = jsonMapper;
        this.jwtProvider = jwtProvider;
        this.appClock = appClock;
    }

    @Override
    public Health health() {
        try {
            var response = send(null, Duration.ofSeconds(2), "health");
            var body = response.body();
            String status = body.path("status").asString("unavailable");
            String db = body.path("db").asString("unknown");
            String llm = body.path("llm").asString("unknown");
            // DB가 정상일 때는 LLM을 끈 환경에서도 규칙 기반 분류와 분석을 실행할 수 있음
            boolean available = response.status() == 200 && "ok".equalsIgnoreCase(status)
                    && "ok".equalsIgnoreCase(db);
            String provider = "off".equals(llm) ? "rules" : null;
            return new Health(available, provider, status, db, llm);
        } catch (AiUnavailableException exception) {
            return new Health(false, null, "unavailable", "unknown", "unknown");
        }
    }

    @Override
    public Classification classify(ClassifyRequest request) {
        return read(send(request, CLASSIFY_TIMEOUT, "classify"), Classification.class);
    }

    @Override
    public Triage triage(String rmaId, String rawText) {
        // 원문은 분류 요청에만 보냄. FastAPI는 접수 번호로 커밋된 QA 입력을 읽음
        return read(send(new ClaimRequest(rmaId), CLASSIFY_TIMEOUT, "triage"), Triage.class);
    }

    @Override
    public AnalysisRegistration registerAnalysis(AnalysisRequest request) {
        var input = new StartRequest(request.rmaId(),
                new Options(request.mode(), request.topK(), request.includeDrafts()));
        var response = send(input, DEFAULT_TIMEOUT, "analyses");
        // 최신 서버는 이미 실행 중인 번호를 202로 반환함. 명세의 409 응답도 같은 번호로 복구함
        JsonNode body = response.body();
        if (response.status() == 409 && "ANALYSIS_IN_PROGRESS".equals(body.path("code").asString(""))) {
            String existingId = body.path("analysis_id").asString("");
            if (existingId.isBlank()) {
                existingId = body.path("detail").path("analysis_id").asString("");
            }
            if (existingId.isBlank()) {
                throw new AiUnavailableException();
            }
            JsonNode existing = getAnalysis(existingId).orElseThrow(AiUnavailableException::new);
            var registered = convert(existing, AnalysisRegistration.class);
            return new AnalysisRegistration(existingId, effectiveCreatedAt(registered), registered.pollAfterMs(),
                    registered.status());
        }
        var registered = read(response, AnalysisRegistration.class);
        if (registered.analysisId() == null || registered.analysisId().isBlank() || registered.createdAt() == null) {
            throw new AiUnavailableException();
        }
        return registered;
    }

    @Override
    public Optional<JsonNode> getAnalysis(String analysisId) {
        var response = send(null, DEFAULT_TIMEOUT, "analyses", analysisId);
        if (response.status() == 404) {
            return Optional.empty();
        }
        return Optional.of(successBody(response));
    }

    @Override
    public SimilarCases findSimilarCases(String rmaId, int limit) {
        // Spring에서 검증한 조회 건수만 쿼리로 전달하고 분석 기준 시각은 FastAPI 계산을 사용함
        URI uri = UriComponentsBuilder.fromUri(baseUrl).pathSegment("claims", rmaId, "similar")
                .queryParam("limit", limit).build().encode().toUri();
        var result = read(send(null, DEFAULT_TIMEOUT, uri), SimilarCases.class);
        if (!rmaId.equals(result.rmaId()) || result.asOf() == null || result.items() == null
                || result.items().size() > limit || !result.items().stream().allMatch(this::validSimilarCase)) {
            throw new AiUnavailableException();
        }
        return result;
    }

    @Override
    public FollowUp followUp(String rmaId, String causeCode) {
        // 버튼 호출이라 응답을 바로 받음. LLM 한 번을 기다릴 수 있어 분류와 같은 제한 시간을 씀
        var body = successBody(send(new FollowUpRequest(rmaId, causeCode), CLASSIFY_TIMEOUT, "followups"));
        String reply = body.path("result").path("customer_reply").asString("");
        if (reply.isBlank()) {
            throw new AiUnavailableException();
        }
        return new FollowUp(reply);
    }

    private boolean validSimilarCase(SimilarCase item) {
        return item != null && item.rmaId() != null && item.openedAt() != null && item.similarity() != null
                && Double.isFinite(item.similarity()) && item.similarity() >= 0 && item.similarity() <= 1
                && (item.correctiveActions() == null || item.correctiveActions().stream().noneMatch(Objects::isNull));
    }

    @Override
    public ChatReply chat(ChatRequest request) {
        var reply = read(send(request, CHAT_TIMEOUT, "chat"), ChatReply.class);
        // 화면이 분기하는 상태값과 답변 본문이 없으면 저장하지 않고 AI 장애로 처리함
        if (reply.answer() == null || reply.answer().isBlank() || !CHAT_STATUSES.contains(reply.status())) {
            log.warn("FastAPI 채팅 응답 형식이 맞지 않음. status={}", reply.status());
            throw new AiUnavailableException();
        }
        return reply;
    }

    @Override
    public boolean refreshCase(CaseRefreshRequest request) {
        return successBody(send(request, DEFAULT_TIMEOUT, "cases", "refresh")).path("indexed").asBoolean(false);
    }

    private OffsetDateTime effectiveCreatedAt(AnalysisRegistration registration) {
        return registration.createdAt() == null ? OffsetDateTime.now(appClock) : registration.createdAt();
    }

    private Response send(Object payload, Duration timeout, String... path) {
        URI uri = UriComponentsBuilder.fromUri(baseUrl).pathSegment(path).build().encode().toUri();
        return send(payload, timeout, uri);
    }

    private Response send(Object payload, Duration timeout, URI uri) {
        var builder = HttpRequest.newBuilder(uri).timeout(timeout)
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + jwtProvider.createServiceAccessToken());
        if (payload != null) {
            builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(jsonMapper.writeValueAsBytes(payload)));
        }

        var request = builder.build();
        var pending = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
        try {
            // 응답 헤더뿐 아니라 본문을 다 받을 때까지 제한 시간을 적용함
            var response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return new Response(response.statusCode(), parseBody(response.body()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException();
        } catch (ExecutionException | TimeoutException e) {
            log.warn("FastAPI 응답을 받지 못함. method={}, cause={}", request.method(), e.getClass().getSimpleName());
            throw new AiUnavailableException();
        } finally {
            if (!pending.isDone()) {
                pending.cancel(true);
            }
        }
    }

    private JsonNode parseBody(byte[] body) {
        try {
            JsonNode parsed = jsonMapper.readTree(body);
            return parsed != null && parsed.isObject() ? parsed : jsonMapper.createObjectNode();
        } catch (JacksonException e) {
            return jsonMapper.createObjectNode();
        }
    }

    private <T> T read(Response response, Class<T> type) {
        return convert(successBody(response), type);
    }

    private <T> T convert(JsonNode body, Class<T> type) {
        try {
            return jsonMapper.treeToValue(body, type);
        } catch (JacksonException e) {
            throw new AiUnavailableException();
        }
    }

    private JsonNode successBody(Response response) {
        if (response.status() >= 200 && response.status() < 300 && !response.body().isEmpty()) {
            return response.body();
        }
        String code = response.body().path("code").asString("");
        if (response.status() == 404 && "CLAIM_NOT_FOUND".equals(code)) {
            throw new ApiException(RmaErrorCode.RMA_NOT_FOUND);
        }
        if (response.status() == 400 && "INVALID_INPUT".equals(code)) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT);
        }
        if (response.status() == 422 && "AS_OF_INVALID".equals(code)) {
            log.warn("FastAPI에서 분석 기준 시각을 거부함. code=AS_OF_INVALID");
            throw new ApiException(CommonErrorCode.INVALID_INPUT);
        }

        // FastAPI 인증 실패는 사용자 로그인 문제로 돌려주지 않음. 응답 본문과 토큰도 로그에 남기지 않음
        log.warn("FastAPI 응답을 처리할 수 없음. status={}", response.status());
        throw new AiUnavailableException();
    }

    private record ClaimRequest(String rmaId) {
    }

    private record FollowUpRequest(String rmaId, String causeCode) {
    }

    private record StartRequest(String rmaId, Options options) {
    }

    private record Options(String mode, int topK, boolean includeDrafts) {
    }

    private record Response(int status, JsonNode body) {
    }
}
