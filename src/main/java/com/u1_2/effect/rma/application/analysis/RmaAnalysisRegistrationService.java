package com.u1_2.effect.rma.application.analysis;

import com.u1_2.effect.config.ai.AiClient;
import com.u1_2.effect.config.ai.AiClient.AnalysisRequest;
import com.u1_2.effect.config.ai.AiErrorCode;
import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.global.exception.CommonErrorCode;
import com.u1_2.effect.rma.application.intake.IntakeWorkflowStateService;
import com.u1_2.effect.rma.domain.RmaClaimRepository;
import com.u1_2.effect.rma.domain.RmaErrorCode;
import com.u1_2.effect.rma.domain.analysis.RmaAnalysisRepository;
import com.u1_2.effect.rma.domain.analysis.RmaAnalysisRepository.ReanalysisInput;
import com.u1_2.effect.rma.presentation.analysis.dto.CreateRmaAnalysisRequest;
import com.u1_2.effect.rma.presentation.analysis.dto.RmaAnalysisRegistrationResponse;
import com.u1_2.effect.user.application.RoleAccess;
import com.u1_2.effect.user.domain.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"local", "test", "prod"})
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RmaAnalysisRegistrationService {

    private static final String DEFAULT_MODE = "B";
    private static final int DEFAULT_TOP_K = 3;

    private final RmaClaimRepository claimRepository;
    private final RmaAnalysisRepository repository;
    private final IntakeWorkflowStateService stateService;
    private final AiClient aiClient;

    @Transactional
    public RmaAnalysisRegistrationResponse register(String rmaId, Role role, CreateRmaAnalysisRequest request) {
        RoleAccess.require(role, Role.QUALITY);
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

        String mode = request == null || request.mode() == null ? DEFAULT_MODE : request.mode().name();
        int topK = request == null || request.topK() == null ? DEFAULT_TOP_K : request.topK();
        return queue(rmaId, input, mode, topK);
    }

    @Transactional
    public void startAfterTriage(String rmaId) {
        // 접수 또는 QA 확인이 끝난 뒤 잠금 안에서 아직 분석을 시작할 상태인지 다시 확인함
        var input = loadForRegistration(rmaId);
        if (!"PROCESSING".equals(input.intakeState()) || !input.triaged() || input.decisionFinalized()) {
            return;
        }
        try {
            queue(rmaId, input, DEFAULT_MODE, DEFAULT_TOP_K);
        } catch (ApiException e) {
            if (e.getErrorCode() != AiErrorCode.AI_UNAVAILABLE) {
                throw e;
            }
            stateService.fail(rmaId, DEFAULT_MODE, "AI 원인분석을 등록하지 못했습니다.");
        }
    }

    private ReanalysisInput loadForRegistration(String rmaId) {
        claimRepository.requireForUpdate(rmaId);
        return repository.findReanalysisInput(rmaId).orElseThrow(() -> new ApiException(RmaErrorCode.RMA_NOT_FOUND));
    }

    private RmaAnalysisRegistrationResponse queue(String rmaId, ReanalysisInput input, String mode, int topK) {
        var registration = aiClient.registerAnalysis(
                new AnalysisRequest(rmaId, mode, topK, true, input.rawText(), input.claimedDefectQty()));
        String state = stateService.saveQueuedAnalysis(rmaId, mode, registration);
        return new RmaAnalysisRegistrationResponse(rmaId, registration.analysisId(), state);
    }
}
