package com.u1_2.effect.rma.application.review;

import com.u1_2.effect.config.ai.AiClient.Triage;
import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.global.exception.CommonErrorCode;
import com.u1_2.effect.global.time.KoreaTime;
import com.u1_2.effect.rma.domain.RmaClaimRepository;
import com.u1_2.effect.rma.domain.RmaErrorCode;
import com.u1_2.effect.rma.domain.review.RmaTriageRepository;
import com.u1_2.effect.rma.domain.review.RmaTriageRepository.Decision;
import com.u1_2.effect.rma.presentation.dto.RmaTriageUpdateResponse;
import com.u1_2.effect.rma.presentation.dto.UpdateRmaTriageRequest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

/**
 * QA 판정 원본·수정 이력·승인 등급을 같은 트랜잭션에서 맞춰 저장함.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RmaTriageStateService {

    private final RmaClaimRepository claimRepository;
    private final RmaTriageRepository repository;
    private final Clock appClock;
    private final JsonMapper jsonMapper;

    @Transactional
    public boolean save(String rmaId, UpdateRmaTriageRequest request, String editor) {
        claimRepository.requireForUpdate(rmaId);

        var context = repository.findContext(rmaId);
        if (context.locked()) {
            throw new ApiException(RmaErrorCode.TRIAGE_LOCKED);
        }

        Decision previous = repository.findLatest(rmaId).orElse(null);
        boolean awaitingReview = "TRIAGE_REVIEW".equals(context.intakeState());
        // 이전 요청이 수동 판정을 저장했지만 분석은 아직 등록하지 않았으면 같은 시작 흐름을 이어감
        boolean confirmedAndStarting = "PROCESSING".equals(context.intakeState()) && previous != null
                && !"QA_AGENT".equals(previous.decidedBy()) && "TRIAGED".equals(context.qaStatus());
        if (!confirmedAndStarting && "PROCESSING".equals(context.intakeState())) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT);
        }
        Triage proposal = awaitingReview && context.proposal() != null
                ? jsonMapper.readValue(context.proposal(), Triage.class) : null;
        if (previous == null && proposal == null) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT);
        }

        String rationale = request.rationale().strip();
        String level = request.analysisLevel().name();
        // 여러 번 수정해도 최초 자동 등급을 기준으로 비교하고 사유의 앞뒤 공백은 길이에서 뺌
        String originalLevel = context.originalLevel() != null ? context.originalLevel()
                : previous != null ? previous.analysisLevel() : proposal.analysisLevel();
        if (level.compareTo(originalLevel) < 0 && rationale.codePointCount(0, rationale.length()) < 10) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT);
        }

        OffsetDateTime now = OffsetDateTime.now(appClock);
        String symbol = StringUtils.hasText(request.characteristicSymbol()) ? request.characteristicSymbol().strip() : null;
        String previousSymbol = previous != null ? previous.characteristicSymbol() : proposal.characteristicSymbol();
        String previousId = previous != null ? previous.characteristicId() : proposal.characteristicId();
        // 같은 표시 기호가 여러 특성에 쓰이므로 기호가 바뀌면 기존 특성 ID는 비움
        var updated = new Decision(previous == null ? "TRI-" + UUID.randomUUID() : previous.triageId(),
                rmaId, request.severity(), Objects.equals(symbol, previousSymbol) ? previousId : null, symbol,
                previous != null ? previous.customerIndustry() : proposal.customerIndustry(),
                previous != null ? previous.repeatSignal() : proposal.repeatSignal(), level, rationale, editor, KoreaTime.formatPcbTime(now));

        repository.save(updated);
        repository.saveHistory(rmaId, previous == null ? context.proposal() : jsonMapper.writeValueAsString(previous),
                jsonMapper.writeValueAsString(updated), editor, now);
        // 완료된 분석은 다시 돌리지 않고 판정 대기 등급만 현재 QA 값으로 맞춤
        repository.updateWaitingLevel(rmaId, level);
        if (awaitingReview) {
            claimRepository.updateQaStatus(rmaId, "TRIAGED");
            repository.markConfirmed(rmaId, now);
        }
        return awaitingReview || confirmedAndStarting;
    }

    public RmaTriageUpdateResponse response(String rmaId) {
        var context = repository.findContext(rmaId);
        var decision = repository.findLatest(rmaId).orElseThrow(() -> new ApiException(RmaErrorCode.RMA_NOT_FOUND));
        return new RmaTriageUpdateResponse(rmaId, decision.analysisLevel(), context.qaStatus(),
                context.intakeState() == null ? "ACCEPTED" : context.intakeState(), decision.decidedBy(),
                KoreaTime.inSeoul(OffsetDateTime.parse(decision.decidedAt())));
    }
}
