package com.u1_2.effect.config.ai;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/**
 * Spring에서 분류·QA·분석·유사 사례를 호출할 때 목업과 실제 FastAPI가 공유하는 계약임.
 */
public interface AiClient {

    Health health();

    Classification classify(ClassifyRequest request);

    Triage triage(String rmaId, String rawText);

    AnalysisRegistration registerAnalysis(AnalysisRequest request);

    Optional<JsonNode> getAnalysis(String analysisId);

    SimilarCases findSimilarCases(String rmaId, int limit);

    boolean refreshCase(CaseRefreshRequest request);

    record Health(boolean available, String provider, String status, String db, String llm) {
    }

    record CaseRefreshRequest(String rmaId, String analysisId, String decision, OffsetDateTime decidedAt) {
    }

    record SimilarCases(String rmaId, OffsetDateTime asOf, List<SimilarCase> items) {
    }

    record SimilarCase(String rmaId, OffsetDateTime openedAt, LocalDate claimDate, String minorCode, String minorName,
                       String causeCode, String causeName, String processId, String equipmentId, String certainty,
                       Double similarity, String basis, String pastAction, Boolean recurrence, Integer daysToResolve,
                       String snippet, List<CorrectiveAction> correctiveActions) {
    }

    record CorrectiveAction(String category, String description, OffsetDateTime implementedAt) {
    }

    record ClassifyRequest(String rmaId, String rawText, String detectionStage, String reportedPartNo, String customerId) {
    }

    record Classification(String status, String majorName, String minorCode, String minorName,
                          List<String> evidenceSpans, List<String> followUpQuestions) {
    }

    record Triage(int severity, String characteristicId, String characteristicSymbol, String customerIndustry,
                  int repeatSignal, String analysisLevel, boolean needsApproval, List<String> approvalReasons,
                  boolean provisional, String rationale, OffsetDateTime asOf) {
    }

    record AnalysisRegistration(String analysisId, OffsetDateTime createdAt, Integer pollAfterMs, String status) {
        public int pollIntervalMs() {
            return pollAfterMs != null && pollAfterMs > 0 ? pollAfterMs : 1000;
        }

        public String pendingState() {
            return "RUNNING".equals(status) ? "RUNNING" : "QUEUED";
        }
    }

    record AnalysisRequest(String rmaId, String mode, int topK, boolean includeDrafts,
                           String rawText, long claimedDefectQty) {
    }
}
