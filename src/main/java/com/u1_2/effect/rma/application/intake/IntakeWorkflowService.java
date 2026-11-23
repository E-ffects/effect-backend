package com.u1_2.effect.rma.application.intake;

import com.u1_2.effect.config.ai.AiClient;
import com.u1_2.effect.config.ai.AiClient.ClassifyRequest;
import com.u1_2.effect.config.ai.AiErrorCode;
import com.u1_2.effect.config.jwt.AuthUser;
import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.rma.application.analysis.RmaAnalysisRegistrationService;
import com.u1_2.effect.rma.domain.intake.RmaIntakeWorkflowRepository;
import com.u1_2.effect.rma.presentation.dto.RmaIntakeResponse;
import com.u1_2.effect.user.application.RoleAccess;
import com.u1_2.effect.user.domain.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 접수 저장 이후 분류 → QA 판정 → 분석 등록을 이어감. 단계마다 저장된 상태로 응답함.
 */
@Slf4j
@Service
@Profile({"local", "test", "prod"})
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IntakeWorkflowService {

    private final IntakeRegistrationService registrationService;
    private final RmaIntakeWorkflowRepository repository;
    private final IntakeWorkflowStateService stateService;
    private final AiClient aiClient;
    private final RmaAnalysisRegistrationService analysisRegistrationService;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RmaIntakeResponse create(IntakeRegistrationService.Registration request, AuthUser user, String key) {
        // 접수 저장을 먼저 커밋하고 새 접수일 때만 AI를 호출함
        var registration = registrationService.register(request, user, key);
        if (registration.created()) {
            try {
                processIntake(registration.rmaId());
            } catch (RuntimeException e) {
                // 접수는 이미 저장됐으므로 실패 상태로 응답하고 재처리 API로 이어가게 함
                log.error("접수 처리 실패 rmaId={}", registration.rmaId(), e);
                stateService.failUnexpected(registration.rmaId());
            }
        }
        return stateService.response(registration.rmaId());
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RmaIntakeResponse retry(String rmaId, Role role) {
        RoleAccess.requireMember(role);
        stateService.beginRetry(rmaId);
        try {
            processIntake(rmaId);
        } catch (RuntimeException e) {
            stateService.failUnexpected(rmaId);
            throw e;
        }
        return stateService.response(rmaId);
    }

    private void processIntake(String rmaId) {
        var input = repository.findInput(rmaId);
        try {
            var classification = aiClient.classify(new ClassifyRequest(rmaId, input.rawText(),
                    input.detectionStage(), input.reportedPartNo(), input.customerId()));
            if (!stateService.saveClassification(input, classification)) {
                return;
            }
            if (!stateService.restoreManualTriage(rmaId)) {
                var triage = aiClient.triage(rmaId, input.rawText());
                stateService.saveTriage(rmaId, triage);
                // 임시 판정은 품질관리자 확인 전까지 분석을 시작하지 않음
                if (triage.provisional()) {
                    return;
                }
            }
        } catch (ApiException e) {
            if (e.getErrorCode() != AiErrorCode.AI_UNAVAILABLE) {
                throw e;
            }
            stateService.fail(rmaId, null, "분류 또는 QA 판정에 필요한 AI 응답을 받지 못했습니다.");
            return;
        }

        analysisRegistrationService.startAfterTriage(rmaId);
    }
}
