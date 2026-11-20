package com.u1_2.effect.rma.application;

import com.u1_2.effect.config.ai.AiClient;
import com.u1_2.effect.config.ai.AiClient.AnalysisRequest;
import com.u1_2.effect.config.ai.AiErrorCode;
import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.global.exception.AuthErrorCode;
import com.u1_2.effect.global.exception.CommonErrorCode;
import com.u1_2.effect.rma.domain.RmaAnalysisRepository;
import com.u1_2.effect.rma.domain.RmaAnalysisRepository.ReanalysisInput;
import com.u1_2.effect.rma.domain.RmaErrorCode;
import com.u1_2.effect.rma.presentation.dto.CreateRmaAnalysisRequest;
import com.u1_2.effect.rma.presentation.dto.RmaAnalysisRegistrationResponse;
import com.u1_2.effect.user.domain.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 접수별 동시 등록을 막고 기존 분석이 진행 중이면 그 번호를 재사용함.
 */
@Service
@Profile({"local", "test"})
@ConditionalOnProperty(name = "ai.mode", havingValue = "mock", matchIfMissing = true)
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RmaReanalysisService {

    private final RmaAnalysisRepository repository;
    private final IntakeWorkflowStateService stateService;
    private final AiClient aiClient;

    @Transactional
    public RmaAnalysisRegistrationResponse register(String rmaId, Role role, CreateRmaAnalysisRequest request) {
        if (role != Role.QUALITY) {
            throw new ApiException(AuthErrorCode.ACCESS_DENIED);
        }
        var input = loadForRegistration(rmaId);
        if (input.decisionFinalized()) {
            throw new ApiException(RmaErrorCode.CAUSE_ALREADY_DECIDED);
        }
        var active = repository.findActiveAnalysis(rmaId);
        if (active.isPresent()) {
            var running = active.get();
            return new RmaAnalysisRegistrationResponse(rmaId, running.analysisId(), running.analysisState());
        }
        if (!input.triaged() || (input.intakeState() != null && !"ACCEPTED".equals(input.intakeState()))) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT);
        }

        String mode = request == null || request.mode() == null ? "B" : request.mode().name();
        int topK = request == null || request.topK() == null ? 3 : request.topK();
        return queue(rmaId, input, mode, topK);
    }

    @Transactional
    public void startAfterTriage(String rmaId) {
        // 동시 확인 요청이 와도 잠금을 얻은 뒤 최신 상태를 다시 확인함
        var input = loadForRegistration(rmaId);
        if (!"PROCESSING".equals(input.intakeState()) || !input.triaged() || input.decisionFinalized()) {
            return;
        }
        try {
            queue(rmaId, input, "B", 3);
        } catch (ApiException e) {
            if (e.getErrorCode() != AiErrorCode.AI_UNAVAILABLE) {
                throw e;
            }
            stateService.fail(rmaId, "B", "AI 원인분석을 등록하지 못했습니다.");
        }
    }

    private ReanalysisInput loadForRegistration(String rmaId) {
        if (!repository.lockRma(rmaId)) {
            throw new ApiException(RmaErrorCode.RMA_NOT_FOUND);
        }
        return repository.findReanalysisInput(rmaId).orElseThrow(() -> new ApiException(RmaErrorCode.RMA_NOT_FOUND));
    }

    private RmaAnalysisRegistrationResponse queue(String rmaId, ReanalysisInput input, String mode, int topK) {
        var registration = aiClient.registerAnalysis(
                new AnalysisRequest(rmaId, mode, topK, true, input.rawText(), input.claimedDefectQty()));
        stateService.saveQueuedAnalysis(rmaId, mode, registration);
        return new RmaAnalysisRegistrationResponse(rmaId, registration.analysisId(), "QUEUED");
    }
}
