package com.u1_2.effect.rma.application.communication;

import com.u1_2.effect.config.jwt.AuthUser;
import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.global.exception.CommonErrorCode;
import com.u1_2.effect.global.time.KoreaTime;
import com.u1_2.effect.rma.domain.RmaClaimRepository;
import com.u1_2.effect.rma.domain.RmaErrorCode;
import com.u1_2.effect.rma.domain.analysis.RmaAnalysisRepository;
import com.u1_2.effect.rma.domain.communication.RmaCommunicationRepository;
import com.u1_2.effect.rma.domain.communication.RmaCommunicationRepository.NewMessage;
import com.u1_2.effect.rma.domain.communication.RmaCommunicationRepository.ServiceAction;
import com.u1_2.effect.rma.presentation.communication.dto.CreateRmaCommunicationRequest;
import com.u1_2.effect.rma.presentation.communication.dto.CreateRmaServiceActionRequest;
import com.u1_2.effect.rma.presentation.communication.dto.CreateRmaServiceActionRequest.ActionType;
import com.u1_2.effect.rma.presentation.communication.dto.CreateRmaServiceActionRequest.Result;
import com.u1_2.effect.rma.presentation.communication.dto.RmaCommunicationResponse;
import com.u1_2.effect.rma.presentation.communication.dto.RmaCommunicationResponse.History;
import com.u1_2.effect.rma.presentation.communication.dto.RmaCommunicationResponse.Item;
import com.u1_2.effect.rma.presentation.communication.dto.RmaServiceActionResponse;
import com.u1_2.effect.user.application.AuthService;
import com.u1_2.effect.user.application.RoleAccess;
import com.u1_2.effect.user.domain.Role;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RmaCommunicationService {

    private final RmaClaimRepository claimRepository;
    private final RmaCommunicationRepository repository;
    private final RmaAnalysisRepository analysisRepository;
    private final AuthService authService;
    private final Clock appClock;

    @Transactional
    public RmaCommunicationResponse recordMessage(String rmaId, CreateRmaCommunicationRequest request, AuthUser user) {
        RoleAccess.require(user.role(), Role.CS);
        validateMessage(request.messageText());
        var claim = claimRepository.requireForUpdate(rmaId);
        if (request.analysisId() != null && !analysisRepository.belongsToRma(request.analysisId(), rmaId)) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT);
        }
        long authorId = authService.getCurrentUser(user.id()).getId();

        String messageId = "MSG-" + UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(appClock);
        // 화면에서 보낸 회신 사실을 기록함. 원문과 작성자 기록은 같은 트랜잭션에 저장함
        repository.insertMessage(new NewMessage(messageId, rmaId, claim.customerId(), request.messageText(),
                KoreaTime.formatPcbTime(now), request.source().name(), request.analysisId(), authorId, now));

        String state = claim.csStatus();
        if ("OPEN".equals(state)) {
            state = "RESPONDED";
            claimRepository.updateCsStatus(rmaId, state);
        }
        return new RmaCommunicationResponse(rmaId, messageId, state, now);
    }

    public History findHistory(String rmaId, Role role) {
        RoleAccess.requireMember(role);
        claimRepository.requireExists(rmaId);
        return new History(repository.findMessages(rmaId, role == Role.QUALITY).stream()
                .map(row -> new Item(row.messageId(), row.audience(), row.authorRole(), row.authorName(),
                        row.messageText(), KoreaTime.inSeoul(row.sentAt())))
                .toList());
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

    private void validateMessage(String text) {
        String trimmed = text.strip();
        if (trimmed.codePointCount(0, trimmed.length()) < 10 || text.codePointCount(0, text.length()) > 4000) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT);
        }
    }
}
