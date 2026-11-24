package com.u1_2.effect.config.ai;

import java.io.IOException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * effect-ai 샘플과 문구별 시나리오로 실제 FastAPI 없이 화면·저장 흐름을 검증함.
 */
@Component
@Profile({"local", "test"})
@ConditionalOnProperty(name = "ai.mode", havingValue = "mock", matchIfMissing = true)
public class MockAiClient implements AiClient {

    private final Clock appClock;
    private final Classification classification;
    private final Triage triage;
    private final JsonMapper jsonMapper;
    private final ObjectNode analysisSample;
    private final SimilarCases similarSample;
    private final ConcurrentHashMap<String, Job> jobs = new ConcurrentHashMap<>();

    public MockAiClient(JsonMapper jsonMapper, Clock appClock) throws IOException {
        this.appClock = appClock;
        this.jsonMapper = jsonMapper;
        try (var source = new ClassPathResource("mock-ai/samples/RMA-08927.json").getInputStream()) {
            var fixture = jsonMapper.readTree(source);
            this.classification = jsonMapper.treeToValue(fixture.required("classify"), Classification.class);
            this.triage = jsonMapper.treeToValue(fixture.required("triage"), Triage.class);
            this.analysisSample = (ObjectNode) fixture.required("analysis");
            var similar = jsonMapper.createObjectNode();
            similar.set("rma_id", fixture.required("rma_id"));
            similar.set("as_of", analysisSample.required("as_of"));
            similar.set("items", analysisSample.required("result").required("similar_cases"));
            this.similarSample = jsonMapper.treeToValue(similar, SimilarCases.class);
        }
    }

    @Override
    public Health health() {
        return new Health(true, "mock", "ok", "not_checked", "off");
    }

    @Override
    public Classification classify(ClassifyRequest request) {
        // 보충 회신이 있으면 최근 입력으로 정보 부족 시나리오를 다시 판단함
        if (IntakeClassificationText.latestSection(request.rawText()).contains("정보 부족")) {
            return new Classification("INSUFFICIENT_INFO", null, null, null, List.of(),
                    List.of("불량이 발생한 공정과 확인한 증상을 알려주세요."));
        }
        return classification;
    }

    @Override
    public Triage triage(String rmaId, String rawText) {
        return new Triage(triage.severity(), triage.characteristicId(), triage.characteristicSymbol(),
                triage.customerIndustry(), triage.repeatSignal(), triage.analysisLevel(), triage.needsApproval(),
                triage.approvalReasons(), rawText.contains("임시 판정"), triage.rationale(), OffsetDateTime.now(appClock));
    }

    @Override
    public AnalysisRegistration registerAnalysis(AnalysisRequest request) {
        String id = "ana-" + UUID.randomUUID();
        jobs.put(id, new Job(request.rmaId(), request.mode(), request.claimedDefectQty(),
                request.rawText().contains("처리 기록 없음"), new AtomicInteger()));
        return new AnalysisRegistration(id, OffsetDateTime.now(appClock), 1000, "QUEUED");
    }

    @Override
    public Optional<JsonNode> getAnalysis(String analysisId) {
        var job = jobs.get(analysisId);
        if (job == null) {
            return Optional.empty();
        }
        int poll = job.polls().incrementAndGet();
        if (poll <= 3) {
            var response = jsonMapper.createObjectNode();
            response.put("rma_id", job.rmaId()).put("analysis_id", analysisId)
                    .put("mode", job.mode()).put("status", "RUNNING");
            response.putObject("progress").put("current_agent", "MANUFACTURING")
                    .put("steps_done", poll).put("steps_total", 7);
            return Optional.of(response);
        }

        // 접수마다 샘플을 복사해 수량을 바꿔도 다른 접수의 원본 제안에 영향이 없도록 함
        ObjectNode response = analysisSample.deepCopy();
        response.put("rma_id", job.rmaId()).put("analysis_id", analysisId).put("mode", job.mode());
        var drafts = (ObjectNode) response.required("result").required("drafts");
        if (job.noServiceHistory()) {
            drafts.putNull("service_action");
        } else if (drafts.get("service_action") instanceof ObjectNode serviceAction) {
            serviceAction.put("board_qty", job.claimedDefectQty());
        }
        return Optional.of(response);
    }

    @Override
    public SimilarCases findSimilarCases(String rmaId, int limit) {
        // 다른 접수에 샘플 사례를 실제 검색 결과처럼 붙이지 않음
        if (!similarSample.rmaId().equals(rmaId)) {
            return new SimilarCases(rmaId, OffsetDateTime.now(appClock), List.of());
        }
        return new SimilarCases(rmaId, similarSample.asOf(), similarSample.items().stream().limit(limit).toList());
    }

    @Override
    public boolean refreshCase(CaseRefreshRequest request) {
        return true;
    }

    private record Job(String rmaId, String mode, long claimedDefectQty, boolean noServiceHistory,
                       AtomicInteger polls) {
    }
}
