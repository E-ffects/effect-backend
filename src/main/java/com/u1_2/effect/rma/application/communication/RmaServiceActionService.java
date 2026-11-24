package com.u1_2.effect.rma.application.communication;

import com.u1_2.effect.global.dto.PageParameters;
import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.global.exception.CommonErrorCode;
import com.u1_2.effect.global.time.KoreaTime;
import com.u1_2.effect.rma.domain.RmaClaimRepository;
import com.u1_2.effect.rma.domain.RmaErrorCode;
import com.u1_2.effect.rma.domain.analysis.RmaAnalysisRepository;
import com.u1_2.effect.rma.domain.communication.RmaServiceActionRepository;
import com.u1_2.effect.rma.domain.communication.RmaServiceActionRepository.ServiceAction;
import com.u1_2.effect.rma.presentation.communication.dto.CreateRmaServiceActionRequest;
import com.u1_2.effect.rma.presentation.communication.dto.CreateRmaServiceActionRequest.ActionType;
import com.u1_2.effect.rma.presentation.communication.dto.CreateRmaServiceActionRequest.Result;
import com.u1_2.effect.rma.presentation.communication.dto.RmaServiceActionHistoryResponse;
import com.u1_2.effect.rma.presentation.communication.dto.RmaServiceActionHistoryResponse.Item;
import com.u1_2.effect.rma.presentation.communication.dto.RmaServiceActionResponse;
import com.u1_2.effect.user.application.RoleAccess;
import com.u1_2.effect.user.domain.Role;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RmaServiceActionService {

    private final RmaClaimRepository claimRepository;
    private final RmaServiceActionRepository repository;
    private final RmaAnalysisRepository analysisRepository;
    private final Clock appClock;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public RmaServiceActionHistoryResponse history(String rmaId, Role role, Integer page, Integer size) {
        RoleAccess.requireMember(role);
        var paging = PageParameters.resolve(page, size);
        claimRepository.requireExists(rmaId);
        var items = repository.findHistory(rmaId, paging).stream()
                .map(row -> new Item(row.actionId(), row.rmaItemId(), row.actionType(), row.policyVersion(),
                        row.boardQty(), row.result(), KoreaTime.inSeoul(row.decidedAt()), KoreaTime.inSeoul(row.executedAt())))
                .toList();
        return new RmaServiceActionHistoryResponse(rmaId, items, repository.countHistory(rmaId), paging.page(), paging.size());
    }

    @Transactional
    public RmaServiceActionResponse recordAction(String rmaId, CreateRmaServiceActionRequest request, Role role) {
        RoleAccess.require(role, Role.CS);
        if (request.boardQty() == 0 && request.actionType() != ActionType.NO_CHARGE_SAMPLE_RETURN) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT);
        }

        var claim = claimRepository.requireForUpdate(rmaId);
        boolean executed = request.result() == Result.EXECUTED;
        // L3는 확정 판정 뒤에만 고객 처리를 실행할 수 있음. 기존 종료 이력의 QA 상태도 인정함
        if (executed && !"CAUSE_DECIDED".equals(claim.qaStatus()) && !"CLOSED".equals(claim.qaStatus())
                && analysisRepository.findLatestQaLevel(rmaId).filter("L3"::equals).isPresent()) {
            throw new ApiException(RmaErrorCode.CAUSE_NOT_DECIDED);
        }

        String itemId = repository.findItemId(rmaId).orElseThrow(() -> new ApiException(CommonErrorCode.INVALID_INPUT));
        String actionId = "ACT-" + UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(appClock);
        String timestamp = KoreaTime.formatPcbTime(now);
        repository.insertServiceAction(new ServiceAction(actionId, itemId, request.actionType().name(),
                request.boardQty(), request.result().name(), timestamp, executed ? timestamp : null));

        // 종료 상태와 실제 실행 시각을 같이 저장해 대시보드 집계 기준을 맞춤
        if (executed) {
            claimRepository.updateCsStatus(rmaId, "CLOSED");
        }
        return new RmaServiceActionResponse(rmaId, actionId, request.actionType().name(), request.result().name(),
                executed ? "CLOSED" : claim.csStatus(), executed ? now : null);
    }
}
