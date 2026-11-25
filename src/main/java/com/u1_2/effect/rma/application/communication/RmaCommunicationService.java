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
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.u1_2.effect.config.ai.AiClient;
import com.u1_2.effect.rma.domain.review.CauseDecisionRepository;
import com.u1_2.effect.rma.presentation.communication.dto.RmaFollowUpDraftResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RmaCommunicationService {

    private final RmaClaimRepository claimRepository;
    private final RmaCommunicationRepository repository;
    private final RmaAnalysisRepository analysisRepository;
    private final AuthService authService;
    private final Clock appClock;
    private final CauseDecisionRepository causeDecisions;
    private final AiClient aiClient;
    private final JsonMapper jsonMapper;

    private static final String DEFAULT_FOLLOW_UP = "원인 분석이 끝났습니다. 확인된 결과에 따라 처리 방안을 진행하며, 진행 상황을 다시 회신드리겠습니다.";

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

    /** 원인 판정(승인·확인)이 끝난 건에만 2차 회신 초안을 만듦. 메일 발송과 기록은 화면의 기존 절차를 따름. */
    public RmaFollowUpDraftResponse createFollowUpDraft(String rmaId, Role role) {
        RoleAccess.require(role, Role.CS);
        claimRepository.requireExists(rmaId);
        var adopted = causeDecisions.findAdopted(rmaId).orElseThrow(() -> new ApiException(RmaErrorCode.FOLLOW_UP_DRAFT_NOT_READY));
        String causeCode = adoptedCauseCode(adopted.candidates())
                .orElseThrow(() -> new ApiException(RmaErrorCode.FOLLOW_UP_DRAFT_NOT_READY));
        try {
            return new RmaFollowUpDraftResponse(rmaId, aiClient.followUp(rmaId, causeCode).customerReply(),
                    adopted.analysisId(), "AI_DRAFT");
        } catch (RuntimeException exception) {
            // AI가 안 되면 원인 내용 없는 기본 문안으로 대신함. 직접 쓴 문안과 같게 MANUAL로 기록됨
            log.warn("2차 회신 초안을 AI로 만들지 못해 기본 문안을 씀. rmaId={}", rmaId);
            return new RmaFollowUpDraftResponse(rmaId, DEFAULT_FOLLOW_UP, adopted.analysisId(), "MANUAL");
        }
    }

    private Optional<String> adoptedCauseCode(String candidatesJson) {
        for (JsonNode candidate : jsonMapper.readTree(candidatesJson)) {
            if ("ADOPTED".equals(candidate.path("status").asString(""))) {
                String code = candidate.has("cause_code") ? candidate.path("cause_code").asString("")
                        : candidate.path("causeCode").asString("");
                return code.isBlank() ? Optional.empty() : Optional.of(code);
            }
        }
        return Optional.empty();
    }

    private void validateMessage(String text) {
        String trimmed = text.strip();
        if (trimmed.codePointCount(0, trimmed.length()) < 10 || text.codePointCount(0, text.length()) > 4000) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT);
        }
    }
}
