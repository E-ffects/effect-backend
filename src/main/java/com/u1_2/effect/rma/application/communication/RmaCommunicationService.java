package com.u1_2.effect.rma.application.communication;

import com.u1_2.effect.config.ai.AiClient;
import com.u1_2.effect.config.jwt.AuthUser;
import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.global.exception.CommonErrorCode;
import com.u1_2.effect.global.time.KoreaTime;
import com.u1_2.effect.rma.domain.RmaClaimRepository;
import com.u1_2.effect.rma.domain.RmaErrorCode;
import com.u1_2.effect.rma.domain.analysis.RmaAnalysisRepository;
import com.u1_2.effect.rma.domain.communication.RmaCommunicationRepository;
import com.u1_2.effect.rma.domain.communication.RmaCommunicationRepository.NewMessage;
import com.u1_2.effect.rma.presentation.communication.dto.CreateRmaCommunicationRequest;
import com.u1_2.effect.rma.presentation.communication.dto.CreateRmaCommunicationRequest.Source;
import com.u1_2.effect.rma.presentation.communication.dto.RmaCommunicationResponse;
import com.u1_2.effect.rma.presentation.communication.dto.RmaCommunicationResponse.History;
import com.u1_2.effect.rma.presentation.communication.dto.RmaCommunicationResponse.Item;
import com.u1_2.effect.rma.presentation.communication.dto.RmaFollowUpDraftResponse;
import com.u1_2.effect.user.application.AuthService;
import com.u1_2.effect.user.application.RoleAccess;
import com.u1_2.effect.user.domain.Role;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final AiClient aiClient;

    public RmaFollowUpDraftResponse createFollowUpDraft(String rmaId, Role role) {
        RoleAccess.require(role, Role.CS);
        claimRepository.requireExists(rmaId);
        var context = repository.findFollowUpContext(rmaId)
                .orElseThrow(() -> new ApiException(RmaErrorCode.FOLLOW_UP_DRAFT_NOT_READY));

        // 별도 생성 API가 생기기 전에는 내부 분석 내용이나 미확정 조치 없이 편집용 문안을 반환함
        String messageText = """
                안녕하세요.

                접수하신 반품 건(%s, 품번 %s)의 품질 검토와 원인 확인을 완료했습니다.
                추가로 확인이 필요한 사항이 있으시면 회신 부탁드립니다.

                감사합니다.
                """.formatted(rmaId, context.reportedPartNo()).strip();
        if (context.causeCode() != null) {
            try {
                // 승인된 원인으로 AI 후속 그래프가 고객용 문안을 씀. 원인은 범주로만 쓰고 처리방안은 한글명만 씀
                String drafted = aiClient.followUp(rmaId, context.causeCode()).customerReply();
                return new RmaFollowUpDraftResponse(rmaId, context.analysisId(), drafted, Source.AI_DRAFT);
            } catch (RuntimeException exception) {
                log.warn("2차 회신 초안을 AI로 만들지 못해 기본 문안을 씀. rmaId={}", rmaId);
            }
        }
        return new RmaFollowUpDraftResponse(rmaId, context.analysisId(), messageText, Source.MANUAL);
    }

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
        repository.insertMessage(new NewMessage(messageId, rmaId, claim.customerId(), "customer", "CS",
                request.messageText(), KoreaTime.formatPcbTime(now), request.source().name(), request.analysisId(),
                authorId, now, null));

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

    private void validateMessage(String text) {
        String trimmed = text.strip();
        if (trimmed.codePointCount(0, trimmed.length()) < 10 || text.codePointCount(0, text.length()) > 4000) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT);
        }
    }
}
