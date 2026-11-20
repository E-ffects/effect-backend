package com.u1_2.effect.rma;

import static org.assertj.core.api.Assertions.assertThat;

import com.u1_2.effect.TestcontainersConfiguration;
import com.u1_2.effect.config.ai.AiClient.AnalysisRequest;
import com.u1_2.effect.config.ai.MockAiClient;
import com.u1_2.effect.rma.application.IntakeWorkflowStateService;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(properties = "app.clock.fixed=2025-12-31T09:00:00+09:00")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Sql({"/sql/meta-fixture.sql", "/sql/rma-intake-fixture.sql"})
class RmaAnalysisSchedulingIntegrationTest {

    @Autowired MockAiClient aiClient;
    @Autowired IntakeWorkflowStateService stateService;
    @Autowired JdbcClient jdbcClient;

    @Test
    void 화면_조회_없이_분석을_저장하고_재시작으로_유실된_작업도_종료한다() throws InterruptedException {
        String rmaId = "RMA-10610";
        var analysis = aiClient.registerAnalysis(new AnalysisRequest(rmaId, "B", 3, true, "일반 접수", 10));
        stateService.saveQueuedAnalysis(rmaId, "B", analysis);
        jdbcClient.sql("""
                INSERT INTO app.ai_analysis (analysis_id, rma_id, mode, analysis_state, created_at)
                VALUES ('old-running', :rmaId, 'B', 'RUNNING', CURRENT_TIMESTAMP)
                """).param("rmaId", rmaId).update();

        waitUntil(analysis.analysisId(), "SUCCEEDED");
        waitUntil("old-running", "FAILED");
        assertThat(jdbcClient.sql("SELECT result_json IS NOT NULL FROM app.ai_analysis WHERE analysis_id = :id")
                .param("id", analysis.analysisId()).query(Boolean.class).single()).isTrue();
        assertThat(jdbcClient.sql("SELECT error_json->>'code' FROM app.ai_analysis WHERE analysis_id = 'old-running'")
                .query(String.class).single()).isEqualTo("ANALYSIS_LOST");
    }

    private void waitUntil(String analysisId, String expectedState) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(12);
        String state;
        do {
            state = jdbcClient.sql("SELECT analysis_state FROM app.ai_analysis WHERE analysis_id = :id")
                    .param("id", analysisId).query(String.class).single();
            if (state.equals(expectedState)) {
                return;
            }
            Thread.sleep(Duration.ofMillis(100));
        } while (Instant.now().isBefore(deadline));
        assertThat(state).as(analysisId).isEqualTo(expectedState);
    }
}
