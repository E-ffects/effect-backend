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

    /** QA가 승인·확인한 원인으로 2차 회신 초안을 만듦. 원인명 없이 범주만 쓴 고객용 문안을 돌려줌. */
    FollowUp followUp(String rmaId, String causeCode);

    /** 에이전트 패널 자유 질의. AI는 대화를 저장하지 않으므로 Spring이 최근 대화와 문맥을 함께 보냄. */
    ChatReply chat(ChatRequest request);

    // ---- 챗봇 계약(초안). 필드 확정은 AI 담당과 합의 후 반영함. context는 범위별 모양이 달라 JSON으로 둠
    record ChatRequest(String threadId, String audience, ChatScopeRef scope, String message,
                       List<ChatTurn> history, JsonNode context) {
    }

    record ChatScopeRef(String type, String id) {
    }

    record ChatTurn(String role, String text) {
    }

    record ChatReply(String status, String answer, List<ChatEvidence> evidence, List<String> warnings,
                     String modelVersion) {
    }

    record ChatEvidence(String type, String rmaId, String label) {
    }

    record FollowUp(String customerReply) {
    }

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
