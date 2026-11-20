package com.u1_2.effect.rma.application;

import com.u1_2.effect.config.ai.AiClient.AnalysisRegistration;
import com.u1_2.effect.config.ai.AiClient.Classification;
import com.u1_2.effect.config.ai.AiClient.Triage;
import com.u1_2.effect.config.ai.AiErrorCode;
import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.global.exception.CommonErrorCode;
import com.u1_2.effect.rma.domain.RmaAttachmentRepository;
import com.u1_2.effect.rma.domain.RmaErrorCode;
import com.u1_2.effect.rma.domain.RmaIntakeWorkflowRepository;
import com.u1_2.effect.rma.domain.RmaIntakeWorkflowRepository.AnalysisRow;
import com.u1_2.effect.rma.domain.RmaIntakeWorkflowRepository.Input;
import com.u1_2.effect.rma.domain.RmaTriageRepository;
import com.u1_2.effect.rma.domain.RmaTriageRepository.Decision;
import com.u1_2.effect.rma.presentation.dto.RmaIntakeResponse;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
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

    private final RmaIntakeWorkflowRepository repository;
    private final RmaAttachmentRepository attachmentRepository;
    private final RmaTriageRepository triageRepository;
    private final JsonMapper jsonMapper;
    private final Clock appClock;

    @Transactional
    public void beginRetry(String rmaId) {
        if (!repository.claimRetry(rmaId, OffsetDateTime.now(appClock))) {
            throw new ApiException(repository.claimExists(rmaId)
                    ? RmaErrorCode.INTAKE_NOT_FAILED : RmaErrorCode.RMA_NOT_FOUND);
        }
        repository.clearAutomaticProposals(rmaId);
        repository.updateQaStatus(rmaId, "RECEIVED");
    }

    @Transactional
    public void failRetry(String rmaId) {
        var error = CommonErrorCode.INTERNAL_ERROR;
        repository.finish(rmaId, "FAILED", error.getErrorCode(), error.getMessage(), OffsetDateTime.now(appClock));
    }

    @Transactional
    public boolean saveClassification(Input input, Classification classification) {
        OffsetDateTime now = OffsetDateTime.now(appClock);
        boolean classified = "CLASSIFIED".equals(classification.status());
        if (classified && repository.insertSymptom("SYM-" + UUID.randomUUID(), input.rmaItemId(),
                classification.minorCode(), jsonMapper.writeValueAsString(classification.evidenceSpans()),
                now.toString()) != 1) {
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
                    triage.repeatSignal(), triage.analysisLevel(), triage.rationale(), "QA_AGENT", now.toString()));
            repository.updateQaStatus(rmaId, "TRIAGED");
        }
        repository.saveTriage(rmaId, jsonMapper.writeValueAsString(triage), triage.provisional(), now);
    }

    @Transactional
    public boolean restoreManualTriage(String rmaId) {
        // 분석 등록 실패를 재처리해도 품질관리자가 확인한 판정은 그대로 씀
        if (triageRepository.findLatest(rmaId).filter(row -> !"QA_AGENT".equals(row.decidedBy())).isEmpty()) {
            return false;
        }
        repository.updateQaStatus(rmaId, "TRIAGED");
        return true;
    }

    @Transactional
    public void saveQueuedAnalysis(String rmaId, String mode, AnalysisRegistration analysis) {
        repository.saveAnalysis(new AnalysisRow(analysis.analysisId(), rmaId, mode, "QUEUED",
                analysis.createdAt(), null, null, analysis.pollIntervalMs()));
        repository.updateQaStatus(rmaId, "ANALYZING");
        repository.finish(rmaId, "ACCEPTED", null, null, OffsetDateTime.now(appClock));
    }

    @Transactional
    public void fail(String rmaId, String analysisMode, String detail) {
        OffsetDateTime now = OffsetDateTime.now(appClock);
        String code = AiErrorCode.AI_UNAVAILABLE.getErrorCode();
        if (analysisMode != null) {
            String error = jsonMapper.writeValueAsString(new RmaIntakeResponse.Failure(code, detail));
            repository.saveAnalysis(new AnalysisRow("failed-" + UUID.randomUUID(), rmaId, analysisMode,
                    "FAILED", now, now, error, 1000));
        }
        repository.finish(rmaId, "FAILED", code, detail, now);
    }

    public RmaIntakeResponse response(String rmaId) {
        var state = repository.findSnapshot(rmaId);
        Classification classification = state.classification() == null ? null
                : jsonMapper.readValue(state.classification(), Classification.class);
        Triage triage = state.triage() == null ? null : jsonMapper.readValue(state.triage(), Triage.class);
        var symptom = classification == null ? null : new RmaIntakeResponse.Symptom(classification.minorCode(),
                classification.minorName(), classification.majorName(), classification.status(), classification.evidenceSpans());
        var triageResponse = triage == null ? null : new RmaIntakeResponse.Triage(triage.severity(),
                triage.characteristicSymbol(), triage.analysisLevel(), triage.needsApproval(), triage.approvalReasons(),
                triage.rationale(), triage.provisional() ? Boolean.TRUE : null);
        if ("TRIAGED".equals(state.qaStatus()) || "ANALYZING".equals(state.qaStatus())) {
            var manual = triageRepository.findLatest(rmaId).filter(row -> !"QA_AGENT".equals(row.decidedBy()));
            if (manual.isPresent()) {
                var row = manual.get();
                var reasons = triage != null && row.analysisLevel().equals(triage.analysisLevel())
                        ? triage.approvalReasons() : List.<String>of();
                triageResponse = new RmaIntakeResponse.Triage(row.severity(), row.characteristicSymbol(),
                        row.analysisLevel(), "L3".equals(row.analysisLevel()), reasons, row.rationale(), null);
            }
        }
        boolean currentAnalysis = "ACCEPTED".equals(state.intakeState())
                || ("FAILED".equals(state.intakeState()) && "TRIAGED".equals(state.qaStatus()));
        var analysis = !currentAnalysis || state.analysisId() == null ? null
                : new RmaIntakeResponse.Analysis(state.analysisId().startsWith("failed-") ? null : state.analysisId(),
                        state.analysisState());
        var failure = state.errorCode() == null ? null : new RmaIntakeResponse.Failure(state.errorCode(), state.errorDetail());
        var attachments = attachmentRepository.findAttachments(rmaId).stream()
                .map(a -> new RmaIntakeResponse.Attachment(a.attachmentId(), a.kind(), a.fileName())).toList();
        var questions = state.questions() == null ? List.<String>of()
                : List.of(jsonMapper.readValue(state.questions(), String[].class));

        return new RmaIntakeResponse(rmaId, state.intakeState(), state.csStatus(), state.qaStatus(),
                symptom, triageResponse, questions, analysis, attachments, failure);
    }
}
