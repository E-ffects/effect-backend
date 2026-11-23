package com.u1_2.effect.rma.application.analysis;

import com.u1_2.effect.config.ai.AiClient;
import com.u1_2.effect.config.ai.AiErrorCode;
import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.rma.domain.RmaClaimRepository;
import com.u1_2.effect.rma.domain.analysis.RmaAnalysisRepository;
import com.u1_2.effect.rma.domain.analysis.RmaAnalysisRepository.Pending;
import com.u1_2.effect.rma.domain.analysis.RmaAnalysisRepository.Update;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 분석 진행과 결과를 영속화함. 완료된 작업은 멈추고 판정 가능한 결과에는 대기 기록을 남김.
 * AI 조회는 트랜잭션 밖에서 하고, 선점 → 조회 → 저장을 각각 짧게 커밋해 DB 커넥션과 잠금을 오래 쥐지 않음.
 */
@Slf4j
@Service
@Profile({"local", "test", "prod"})
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RmaAnalysisPollingService {

    private final RmaClaimRepository claimRepository;
    private final RmaAnalysisRepository repository;
    private final AiClient aiClient;
    private final JsonMapper jsonMapper;
    private final Clock appClock;
    private final TransactionTemplate transactionTemplate;

    /** AI 응답 대기(최대 5초)보다 길게 잡아 조회 도중 다른 실행기가 같은 작업을 집지 않게 함 */
    static final int LEASE_SECONDS = 30;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void poll(String analysisId) {
        repository.claimDueAnalysis(analysisId, LEASE_SECONDS).ifPresent(this::pollPending);
    }

    private void pollPending(Pending pending) {
        JsonNode response;
        try {
            response = aiClient.getAnalysis(pending.analysisId()).orElse(null);
        } catch (ApiException e) {
            if (e.getErrorCode() != AiErrorCode.AI_UNAVAILABLE) {
                throw e;
            }
            // 일시적인 AI 장애는 다음 조회 때 재시도하고 제한 시간이 지나면 종료함
            if (pending.timedOut()) {
                timeout(pending);
            } else {
                repository.reschedule(pending.analysisId(), pending.claimedUntil());
            }
            return;
        }
        // 서버에서 분석 번호를 더는 찾을 수 없으면 재시작 등으로 작업이 유실된 것으로 기록함
        if (response == null) {
            fail(pending, "ANALYSIS_LOST", "AI 서버에 기존 분석 작업이 없습니다.");
            return;
        }
        if (pending.timedOut()) {
            timeout(pending);
            return;
        }

        String state = response.path("status").asString("");
        switch (state) {
            case "QUEUED", "RUNNING", "SUCCEEDED", "PARTIAL", "FAILED" -> {
                try {
                    transactionTemplate.executeWithoutResult(status -> saveResponse(pending, state, response));
                } catch (DateTimeParseException e) {
                    fail(pending, "ANALYSIS_INVALID_RESPONSE", "AI 분석 기준 시각을 읽을 수 없습니다.");
                }
            }
            default -> fail(pending, "ANALYSIS_INVALID_RESPONSE", "AI 분석 상태를 확인할 수 없습니다.");
        }
    }

    private void saveResponse(Pending pending, String state, JsonNode response) {
        boolean hasResult = state.equals("SUCCEEDED") || state.equals("PARTIAL");
        if (hasResult && !response.path("result").isObject()) {
            fail(pending, "ANALYSIS_INVALID_RESPONSE", "AI 분석 결과를 확인할 수 없습니다.");
            return;
        }
        boolean completed = hasResult || state.equals("FAILED");
        OffsetDateTime now = OffsetDateTime.now(appClock);
        String asOf = response.path("as_of").asString(null);
        String error = state.equals("FAILED") ? json(response.get("error")) : null;
        if (state.equals("FAILED") && error == null) {
            error = error("ANALYSIS_FAILED", "AI 원인분석을 완료하지 못했습니다.");
        }

        boolean saved = save(new Update(pending.analysisId(), state, asOf == null ? null : OffsetDateTime.parse(asOf),
                json(response.get("progress")), hasResult ? json(response.get("result")) : null,
                completed ? json(response.get("trace")) : null, json(response.get("warnings")), error,
                completed ? response.path("langsmith_run_id").asString(null) : null, completed ? now : null, now,
                pending.claimedUntil()));

        if (saved && hasResult) {
            // 시점이 다른 AI 결과보다 DB에 저장된 최신 QA 분석 수준을 우선함
            claimRepository.findForUpdate(pending.rmaId());
            String level = repository.findLatestQaLevel(pending.rmaId())
                    .orElseGet(() -> response.at("/result/qa_review/analysis_level").asString(null));
            if (level != null) {
                repository.createWaitingDecision(pending.rmaId(), pending.analysisId(), level);
            }
        }
    }

    private String json(JsonNode node) {
        return node == null || node.isNull() ? null : jsonMapper.writeValueAsString(node);
    }

    private String error(String code, String detail) {
        return jsonMapper.createObjectNode().put("code", code).put("detail", detail).toString();
    }

    private void timeout(Pending pending) {
        fail(pending, "ANALYSIS_TIMEOUT", "원인분석 대기 시간이 초과됐습니다.");
    }

    private void fail(Pending pending, String code, String detail) {
        OffsetDateTime now = OffsetDateTime.now(appClock);
        save(new Update(pending.analysisId(), "FAILED", null, null, null, null, null,
                error(code, detail), null, now, now, pending.claimedUntil()));
    }

    private boolean save(Update update) {
        boolean saved = repository.save(update);
        if (!saved) {
            log.info("선점이 끝났거나 이미 종료된 분석이라 응답을 버립니다. analysisId={}", update.analysisId());
        }
        return saved;
    }
}
