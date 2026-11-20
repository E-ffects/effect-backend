package com.u1_2.effect.config.ai;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/**
 * Spring에서 사용하는 분류·QA·분석 호출 계약임. 지금은 목업이 구현하고 실제 FastAPI도 같은 진입점을 사용함.
 */
public interface AiClient {

    Classification classify(ClassifyRequest request);

    Triage triage(String rmaId, String rawText);

    AnalysisRegistration registerAnalysis(AnalysisRequest request);

    Optional<JsonNode> getAnalysis(String analysisId);

    record ClassifyRequest(String rmaId, String rawText, String detectionStage, String reportedPartNo, String customerId) {
    }

    record Classification(String status, String majorName, String minorCode, String minorName,
                          List<String> evidenceSpans, List<String> followUpQuestions) {
    }

    record Triage(int severity, String characteristicId, String characteristicSymbol, String customerIndustry,
                  int repeatSignal, String analysisLevel, boolean needsApproval, List<String> approvalReasons,
                  boolean provisional, String rationale, OffsetDateTime asOf) {
    }

    record AnalysisRegistration(String analysisId, OffsetDateTime createdAt, Integer pollAfterMs) {
        public int pollIntervalMs() {
            return pollAfterMs != null && pollAfterMs > 0 ? pollAfterMs : 1000;
        }
    }

    record AnalysisRequest(String rmaId, String mode, int topK, boolean includeDrafts,
                           String rawText, long claimedDefectQty) {
    }
}
