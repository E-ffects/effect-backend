package com.u1_2.effect.rma.application.analysis;

import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.global.time.KoreaTime;
import com.u1_2.effect.rma.domain.RmaErrorCode;
import com.u1_2.effect.rma.domain.analysis.RmaAnalysisRepository;
import com.u1_2.effect.rma.presentation.analysis.dto.RmaAnalysisResponse;
import com.u1_2.effect.user.domain.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * AI를 다시 호출하지 않고 DB에 저장된 최신 분석을 읽어 역할에 맞춰 반환함.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RmaAnalysisService {

    private final RmaAnalysisRepository repository;
    private final JsonMapper jsonMapper;

    public RmaAnalysisResponse getAnalysis(String rmaId, Role role) {
        var snapshot = repository.findLatest(rmaId)
                .orElseThrow(() -> new ApiException(RmaErrorCode.RMA_NOT_FOUND));
        String state = snapshot.analysisState() == null ? "NOT_REQUESTED" : snapshot.analysisState();
        boolean quality = role == Role.QUALITY;
        JsonNode progress = state.equals("RUNNING") ? read(snapshot.progress()) : null;
        JsonNode result = state.equals("SUCCEEDED") || state.equals("PARTIAL") ? read(snapshot.result()) : null;
        JsonNode error = state.equals("FAILED") ? read(snapshot.error()) : null;
        JsonNode warnings = quality ? read(snapshot.warnings()) : null;
        JsonNode trace = quality ? read(snapshot.trace()) : null;
        String analysisId = snapshot.analysisId();

        if (!quality) {
            // CS에는 진행 숫자만 유지하고 내부 에이전트명과 오류 상세는 빼고 반환함
            if (progress instanceof ObjectNode object) {
                object.remove("current_agent");
            }
            if (result != null) {
                result = customerResult(result);
            }
            if (error != null) {
                error = jsonMapper.createObjectNode().put("code", error.path("code").asString("ANALYSIS_FAILED"))
                        .put("detail", "원인분석을 완료하지 못했습니다.");
            }
        }
        if (analysisId != null && analysisId.startsWith("failed-")) {
            analysisId = null;
        }

        return new RmaAnalysisResponse(rmaId, analysisId, state, KoreaTime.inSeoul(snapshot.asOf()), snapshot.mode(),
                progress, result, quality && trace == null ? jsonMapper.nullNode() : trace,
                warnings == null ? jsonMapper.createArrayNode() : warnings, error, KoreaTime.inSeoul(snapshot.updatedAt()),
                quality ? snapshot.langsmithRunId() : null);
    }

    private ObjectNode customerResult(JsonNode result) {
        // AI 응답에 필드가 추가돼도 CS는 명세에 허용된 필드만 받음
        var filtered = jsonMapper.createObjectNode();
        if (result.has("classification")) {
            filtered.set("classification", result.get("classification"));
        }
        if (result.path("qa_review").isObject()) {
            var qa = filtered.putObject("qa_review");
            copyIfPresent(result.get("qa_review"), qa, "analysis_level");
            copyIfPresent(result.get("qa_review"), qa, "needs_approval");
        }
        if (result.path("drafts").isObject()) {
            var drafts = filtered.putObject("drafts");
            copyIfPresent(result.get("drafts"), drafts, "customer_reply");
            copyIfPresent(result.get("drafts"), drafts, "service_action");
        }
        return filtered;
    }

    private void copyIfPresent(JsonNode source, ObjectNode target, String field) {
        if (source.has(field)) {
            target.set(field, source.get(field));
        }
    }

    private JsonNode read(String value) {
        return value == null ? null : jsonMapper.readTree(value);
    }
}
