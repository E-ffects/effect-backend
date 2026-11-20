package com.u1_2.effect.config.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.u1_2.effect.config.ai.AiClient.AnalysisRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class MockAiClientTest {

    private static final String SERVICE_ACTION = "/result/drafts/service_action";
    private final JsonMapper mapper = JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();

    @Test
    void 목업은_샘플_제안을_그대로_사용하고_접수_수량만_적용한다() throws Exception {
        var mock = client();
        var job = mock.registerAnalysis(new AnalysisRequest("RMA-10611", "B", 3, true, "불량 접수", 37));
        for (int step = 1; step <= 3; step++) {
            JsonNode running = mock.getAnalysis(job.analysisId()).orElseThrow();
            assertThat(running.path("status").stringValue()).isEqualTo("RUNNING");
            assertThat(running.at("/progress/steps_done").intValue()).isEqualTo(step);
        }

        JsonNode analysis = mock.getAnalysis(job.analysisId()).orElseThrow();
        assertThat(analysis.path("status").stringValue()).isEqualTo("SUCCEEDED");
        try (var sample = new ClassPathResource("mock-ai/samples/RMA-08927.json").getInputStream()) {
            var expected = (ObjectNode) mapper.readTree(sample).path("analysis").at(SERVICE_ACTION);
            expected.put("board_qty", 37L);
            assertThat(analysis.at(SERVICE_ACTION)).isEqualTo(expected);
        }
    }

    @Test
    void 처리_기록_없음은_제안이_null이며_다른_접수에는_영향이_없다() throws Exception {
        var mock = client();
        assertThat(completedAction(mock, "처리 기록 없음", 9).isNull()).isTrue();
        assertThat(completedAction(mock, "일반 접수", 3).path("board_qty").longValue()).isEqualTo(3);
    }

    private MockAiClient client() throws Exception {
        return new MockAiClient(mapper, Clock.fixed(Instant.parse("2025-12-31T00:00:00Z"), ZoneId.of("Asia/Seoul")));
    }

    private JsonNode completedAction(MockAiClient mock, String rawText, long quantity) {
        var job = mock.registerAnalysis(new AnalysisRequest("RMA-10611", "B", 3, true, rawText, quantity));
        for (int i = 0; i < 3; i++) {
            mock.getAnalysis(job.analysisId());
        }
        return mock.getAnalysis(job.analysisId()).orElseThrow().at(SERVICE_ACTION);
    }
}
