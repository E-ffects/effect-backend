package com.u1_2.effect.rma.application.intake;

import com.u1_2.effect.config.ai.AiClient.Classification;
import com.u1_2.effect.config.ai.AiClient.Triage;
import com.u1_2.effect.rma.domain.RmaAttachmentRepository;
import com.u1_2.effect.rma.domain.intake.RmaIntakeWorkflowRepository;
import com.u1_2.effect.rma.domain.intake.RmaIntakeWorkflowRepository.Snapshot;
import com.u1_2.effect.rma.domain.review.RmaTriageRepository;
import com.u1_2.effect.rma.presentation.intake.dto.RmaIntakeResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IntakeResponseQuery {

    private final RmaIntakeWorkflowRepository repository;
    private final RmaAttachmentRepository attachmentRepository;
    private final RmaTriageRepository triageRepository;
    private final JsonMapper jsonMapper;

    // 신규 접수·보충 회신·재처리 모두 DB에 커밋된 상태를 같은 응답 형태로 읽음
    public RmaIntakeResponse find(String rmaId) {
        var state = repository.findSnapshot(rmaId);
        Classification classification = state.classification() == null ? null
                : jsonMapper.readValue(state.classification(), Classification.class);
        var symptom = classification == null ? null : new RmaIntakeResponse.Symptom(classification.minorCode(),
                classification.minorName(), classification.majorName(), classification.status(), classification.evidenceSpans());
        // 재처리 중 과거 분석 번호가 남아 있어도 현재 단계에 해당하는 분석만 보여 줌
        boolean currentAnalysis = "ACCEPTED".equals(state.intakeState())
                || ("FAILED".equals(state.intakeState()) && "TRIAGED".equals(state.qaStatus()));
        var analysis = !currentAnalysis || state.analysisId() == null ? null
                : new RmaIntakeResponse.Analysis(state.analysisId().startsWith("failed-") ? null : state.analysisId(),
                        state.analysisState());
        var feedback = IntakeFeedback.fromStored(jsonMapper, state.questions(), state.errorCode(), state.errorDetail());
        var attachments = attachmentRepository.findAttachments(rmaId).stream()
                .map(a -> new RmaIntakeResponse.Attachment(a.attachmentId(), a.kind(), a.fileName())).toList();
        // 접수 응답은 질문이 없을 때 빈 배열을 내려주는 기존 형식을 유지함
        var questions = feedback.questions() == null ? List.<String>of() : feedback.questions();

        return new RmaIntakeResponse(rmaId, state.intakeState(), state.csStatus(), state.qaStatus(),
                symptom, responseTriage(state), questions, analysis, attachments, feedback.error());
    }

    private RmaIntakeResponse.Triage responseTriage(Snapshot state) {
        Triage automatic = state.triage() == null ? null : jsonMapper.readValue(state.triage(), Triage.class);
        if ("TRIAGED".equals(state.qaStatus()) || "ANALYZING".equals(state.qaStatus())) {
            // 화면에는 자동 판정안보다 품질관리자가 저장한 판정을 우선 표시함
            var manual = triageRepository.findManual(state.rmaId());
            if (manual.isPresent()) {
                var decision = manual.get();
                var reasons = automatic != null && decision.analysisLevel().equals(automatic.analysisLevel())
                        ? automatic.approvalReasons() : List.<String>of();
                return new RmaIntakeResponse.Triage(decision.severity(), decision.characteristicSymbol(),
                        decision.analysisLevel(), "L3".equals(decision.analysisLevel()), reasons, decision.rationale(), null);
            }
        }
        return automatic == null ? null : new RmaIntakeResponse.Triage(automatic.severity(),
                automatic.characteristicSymbol(), automatic.analysisLevel(), automatic.needsApproval(),
                automatic.approvalReasons(), automatic.rationale(), automatic.provisional() ? Boolean.TRUE : null);
    }
}
