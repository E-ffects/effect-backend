package com.u1_2.effect.rma.application.intake;

import com.u1_2.effect.config.ai.AiClient.AnalysisRegistration;
import com.u1_2.effect.config.ai.AiClient.Classification;
import com.u1_2.effect.config.ai.AiClient.Triage;
import com.u1_2.effect.config.ai.AiErrorCode;
import com.u1_2.effect.config.jwt.AuthUser;
import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.global.exception.CommonErrorCode;
import com.u1_2.effect.global.time.KoreaTime;
import com.u1_2.effect.rma.domain.RmaClaimRepository;
import com.u1_2.effect.rma.domain.RmaErrorCode;
import com.u1_2.effect.rma.domain.analysis.RmaAnalysisRepository;
import com.u1_2.effect.rma.domain.analysis.RmaAnalysisRepository.AnalysisRow;
import com.u1_2.effect.rma.domain.communication.RmaCommunicationRepository;
import com.u1_2.effect.rma.domain.communication.RmaCommunicationRepository.NewMessage;
import com.u1_2.effect.rma.domain.intake.RmaIntakeWorkflowRepository;
import com.u1_2.effect.rma.domain.intake.RmaIntakeWorkflowRepository.Input;
import com.u1_2.effect.rma.domain.review.RmaTriageRepository;
import com.u1_2.effect.rma.domain.review.RmaTriageRepository.Decision;
import com.u1_2.effect.rma.presentation.intake.dto.IntakeErrorResponse;
import com.u1_2.effect.user.application.AuthService;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI 호출 단계마다 필요한 DB 변경을 커밋함. 호출이 실패해도 이미 저장한 접수와 이력이 남음.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IntakeWorkflowStateService {

    private final RmaClaimRepository claimRepository;
    private final RmaIntakeWorkflowRepository repository;
    private final RmaAnalysisRepository analysisRepository;
    private final RmaTriageRepository triageRepository;
    private final RmaCommunicationRepository communicationRepository;
    private final AuthService authService;
    private final JsonMapper jsonMapper;
    private final Clock appClock;

    @Transactional
    public void beginRetry(String rmaId) {
        claimRepository.requireForUpdate(rmaId);
        beginProcessing(rmaId, "FAILED", RmaErrorCode.INTAKE_NOT_FAILED, OffsetDateTime.now(appClock));
    }

    @Transactional
    public boolean beginReply(String rmaId, String messageText, AuthUser user, String key) {
        // 같은 접수에 회신을 동시에 등록해도 한 건씩 중복과 상태를 확인함
        var claim = claimRepository.requireForUpdate(rmaId);
        long authorId = authService.getCurrentUser(user.id()).getId();
        if (key != null) {
            var prior = communicationRepository.findCustomerSupplement(rmaId, authorId, key);
            if (prior.isPresent()) {
                if (!prior.get().equals(messageText)) {
                    throw new ApiException(RmaErrorCode.INTAKE_REPLY_REQUEST_CONFLICT);
                }
                return false;
            }
        }

        OffsetDateTime now = OffsetDateTime.now(appClock);
        beginProcessing(rmaId, "NEEDS_INFO", RmaErrorCode.INTAKE_NOT_NEEDS_INFO, now);
        // 접수 상태와 받은 회신을 같은 트랜잭션에 저장한 뒤 AI를 호출함
        communicationRepository.insertMessage(new NewMessage("MSG-" + UUID.randomUUID(), rmaId,
                claim.customerId(), "CS", "customer", messageText, KoreaTime.formatPcbTime(now),
                "CUSTOMER_SUPPLEMENT", null, authorId, null, key));
        return true;
    }

    private void beginProcessing(String rmaId, String expectedState, RmaErrorCode error, OffsetDateTime now) {
        if (!repository.claimForProcessing(rmaId, expectedState, now)) {
            throw new ApiException(error);
        }
        repository.clearAutomaticProposals(rmaId);
        claimRepository.updateQaStatus(rmaId, "RECEIVED");
    }

    @Transactional
    public void failUnexpected(String rmaId) {
        // AI 장애 외의 예외로 처리가 끊겨도 PROCESSING에 남지 않게 해서 재처리 API로 복구할 수 있게 함
        var error = CommonErrorCode.INTERNAL_ERROR;
        repository.failIfProcessing(rmaId, error.getErrorCode(), error.getMessage(), OffsetDateTime.now(appClock));
    }

    @Transactional
    public boolean saveClassification(Input input, Classification classification) {
        OffsetDateTime now = OffsetDateTime.now(appClock);
        boolean classified = "CLASSIFIED".equals(classification.status());
        if (classified && repository.insertSymptom("SYM-" + UUID.randomUUID(), input.rmaItemId(),
                classification.minorCode(), jsonMapper.writeValueAsString(classification.evidenceSpans()),
                KoreaTime.formatPcbTime(now)) != 1) {
            throw new ApiException(AiErrorCode.AI_UNAVAILABLE);
        }
        repository.saveClassification(input.rmaId(), classified ? "PROCESSING" : "NEEDS_INFO",
                jsonMapper.writeValueAsString(classification),
                jsonMapper.writeValueAsString(classification.followUpQuestions()), now);
        return classified;
    }

    @Transactional
    public void saveTriage(String rmaId, Triage triage) {
        OffsetDateTime now = OffsetDateTime.now(appClock);
        // 임시 판정안은 app에 보관하고 확정된 판정만 pcb에 등록함
        if (!triage.provisional()) {
            triageRepository.save(new Decision("TRI-" + UUID.randomUUID(), rmaId, triage.severity(),
                    triage.characteristicId(), triage.characteristicSymbol(), triage.customerIndustry(),
                    triage.repeatSignal(), triage.analysisLevel(), triage.rationale(), "QA_AGENT", KoreaTime.formatPcbTime(now)));
            claimRepository.updateQaStatus(rmaId, "TRIAGED");
        }
        repository.saveTriage(rmaId, jsonMapper.writeValueAsString(triage), triage.provisional(), now);
    }

    @Transactional
    public boolean restoreManualTriage(String rmaId) {
        // 분석 등록 실패를 재처리해도 품질관리자가 확인한 판정은 그대로 씀
        if (triageRepository.findManual(rmaId).isEmpty()) {
            return false;
        }
        claimRepository.updateQaStatus(rmaId, "TRIAGED");
        return true;
    }

    @Transactional
    public String saveQueuedAnalysis(String rmaId, String mode, AnalysisRegistration analysis) {
        OffsetDateTime now = OffsetDateTime.now(appClock);
        // FastAPI가 기존 작업 번호를 반환하면 저장된 진행 상태를 덮어쓰지 않음
        analysisRepository.insertAnalysis(new AnalysisRow(analysis.analysisId(), rmaId, mode, analysis.pendingState(),
                analysis.createdAt(), now, null, null, analysis.pollIntervalMs()));
        String state = analysisRepository.findRegisteredState(rmaId, analysis.analysisId());
        claimRepository.updateQaStatus(rmaId, "ANALYZING");
        repository.finish(rmaId, "ACCEPTED", null, null, now);
        return state;
    }

    @Transactional
    public void fail(String rmaId, String analysisMode, String detail) {
        OffsetDateTime now = OffsetDateTime.now(appClock);
        String code = AiErrorCode.AI_UNAVAILABLE.getErrorCode();
        if (analysisMode != null) {
            String error = jsonMapper.writeValueAsString(new IntakeErrorResponse(code, detail));
            analysisRepository.insertAnalysis(new AnalysisRow("failed-" + UUID.randomUUID(), rmaId, analysisMode,
                    "FAILED", now, now, now, error, 1000));
        }
        repository.finish(rmaId, "FAILED", code, detail, now);
    }
}
