package com.u1_2.effect.rma.application;

import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.rma.domain.RmaDetailRepository;
import com.u1_2.effect.rma.domain.RmaDetailRepository.Claim;
import com.u1_2.effect.rma.domain.RmaErrorCode;
import com.u1_2.effect.rma.presentation.dto.RmaDetailResponse;
import com.u1_2.effect.rma.presentation.dto.RmaDetailResponse.Customer;
import com.u1_2.effect.rma.presentation.dto.RmaDetailResponse.CustomerTriage;
import com.u1_2.effect.rma.presentation.dto.RmaDetailResponse.Item;
import com.u1_2.effect.rma.presentation.dto.RmaDetailResponse.QualityTriage;
import com.u1_2.effect.rma.presentation.dto.RmaDetailResponse.Symptom;
import com.u1_2.effect.rma.presentation.dto.RmaDetailResponse.Triage;
import com.u1_2.effect.user.domain.Role;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 접수·품목·분류·QA 판정을 조합해 한 건의 상세 화면에 필요한 값을 만듦.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RmaDetailService {

    private final RmaDetailRepository rmaDetailRepository;

    public RmaDetailResponse getDetail(String rmaId, Role role) {
        Claim claim = rmaDetailRepository.findClaim(rmaId)
                .orElseThrow(() -> new ApiException(RmaErrorCode.RMA_NOT_FOUND));
        // 고객이 신고한 Lot은 유지하고 내부 추적 Lot과 QA 상세는 QUALITY에만 내려줌
        boolean quality = role == Role.QUALITY;

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

        return new RmaDetailResponse(
                claim.rmaId(), claim.openedAt(), claim.occurredAt(), claim.rawText(), claim.reportedPartNo(),
                claim.reportedLotNo(), claim.reportedDateCode(), claim.detectionStage(), claim.csStatus(),
                new Customer(claim.customerId(), claim.customerName(), claim.industry()), items, symptom, triage,
                quality ? claim.qaStatus() : null, quality ? claim.approvalStatus() : null,
                claim.intakeState(), claim.analysisState());
    }
}
