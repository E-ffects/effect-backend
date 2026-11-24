package com.u1_2.effect.rma.application;

import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.global.time.KoreaTime;
import com.u1_2.effect.recurrence.domain.RecurrenceDecisionRepository;
import com.u1_2.effect.report.domain.ReportRepository;
import com.u1_2.effect.rma.application.intake.IntakeFeedback;
import com.u1_2.effect.rma.domain.RmaDetailRepository.Claim;
import com.u1_2.effect.rma.domain.RmaDetailRepository;
import com.u1_2.effect.rma.domain.RmaErrorCode;
import com.u1_2.effect.rma.domain.communication.RmaCommunicationRepository;
import com.u1_2.effect.rma.presentation.dto.RmaDetailResponse.Customer;
import com.u1_2.effect.rma.presentation.dto.RmaDetailResponse.CustomerTriage;
import com.u1_2.effect.rma.presentation.dto.RmaDetailResponse.Item;
import com.u1_2.effect.rma.presentation.dto.RmaDetailResponse.QualityTriage;
import com.u1_2.effect.rma.presentation.dto.RmaDetailResponse.ReportStatus;
import com.u1_2.effect.rma.presentation.dto.RmaDetailResponse.Symptom;
import com.u1_2.effect.rma.presentation.dto.RmaDetailResponse.Triage;
import com.u1_2.effect.rma.presentation.dto.RmaDetailResponse.TriageProposal;
import com.u1_2.effect.rma.presentation.dto.RmaDetailResponse;
import com.u1_2.effect.user.domain.Role;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 접수·품목·분류·QA 판정을 조합해 한 건의 상세 화면에 필요한 값을 만듦.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RmaDetailService {

    private final RmaDetailRepository rmaDetailRepository;
    private final RmaCommunicationRepository communications;
    private final ReportRepository reportRepository;
    private final RecurrenceDecisionRepository recurrenceDecisions;
    private final JsonMapper jsonMapper;

    public RmaDetailResponse getDetail(String rmaId, Role role) {
        // 고객이 신고한 Lot은 유지하고 내부 추적 Lot과 QA 상세는 QUALITY에만 내려줌
        boolean quality = role == Role.QUALITY;
        Claim claim = rmaDetailRepository.findClaim(rmaId, quality)
                .orElseThrow(() -> new ApiException(RmaErrorCode.RMA_NOT_FOUND));

        List<Item> items = rmaDetailRepository.findItems(rmaId).stream()
                .map(item -> new Item(item.rmaItemId(), item.panelId(), item.inputQty(), item.claimedDefectQty(),
                        item.returnedQty(), item.verifiedDefectQty(), quality ? item.lotId() : null))
                .toList();
        Symptom symptom = rmaDetailRepository.findSymptom(rmaId)
                .map(row -> new Symptom(row.primaryLabel(), row.minorName(), row.majorName(),
                        row.status(), row.evidenceSpans()))
                .orElse(null);
        Triage triage = rmaDetailRepository.findTriage(rmaId)
                .<Triage>map(row -> quality
                        ? new QualityTriage(row.severity(), row.characteristicSymbol(), row.analysisLevel(),
                                row.rationale(), row.decidedBy(), row.decidedAt())
                        : new CustomerTriage(row.analysisLevel()))
                .orElse(null);

        // 확정 판정과 임시 판정안을 별도 필드로 내려 화면에서 판정 확인 상태를 구분하게 함
        TriageProposal proposal = claim.triageProposal() == null ? null
                : jsonMapper.readValue(claim.triageProposal(), TriageProposal.class);
        var recurrence = quality ? recurrenceDecisions.find(rmaId).orElse(null) : null;
        // CS에는 내부 판정 대신 초안 API와 같은 조건으로 계산한 버튼 사용 가능 여부만 줌
        Boolean followUpDraftAvailable = quality ? null : communications.findFollowUpContext(rmaId).isPresent();
        // 접수 화면을 다시 열어도 현재 상태에 해당하는 보충 질문이나 접수 실패 안내를 복원함
        var feedback = IntakeFeedback.fromStored(jsonMapper, claim.followUpQuestions(),
                claim.intakeErrorCode(), claim.intakeErrorDetail());
        return new RmaDetailResponse(
                claim.rmaId(), claim.openedAt(), claim.occurredAt(), claim.rawText(), claim.reportedPartNo(),
                claim.reportedLotNo(), claim.reportedDateCode(), claim.detectionStage(), claim.csStatus(),
                new Customer(claim.customerId(), claim.customerName(), claim.industry()), items, symptom, triage, proposal,
                quality ? claim.qaStatus() : null, quality ? claim.approvalStatus() : null,
                claim.intakeState(), claim.analysisState(), quality ? reportStatus(rmaId) : null,
                recurrence == null ? null : recurrence.decision(), recurrence == null ? null : recurrence.decidedBy(),
                recurrence == null ? null : KoreaTime.inSeoul(recurrence.decidedAt()), followUpDraftAvailable,
                feedback.questions(), feedback.error());
    }

    private ReportStatus reportStatus(String rmaId) {
        var status = reportRepository.status(rmaId);
        return new ReportStatus(status.exists(), status.reviewed());
    }
}
