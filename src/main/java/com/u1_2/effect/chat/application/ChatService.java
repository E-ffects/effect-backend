package com.u1_2.effect.chat.application;

import com.u1_2.effect.chat.application.ChatThreadService.Answer;
import com.u1_2.effect.chat.application.context.ChatContextProvider;
import com.u1_2.effect.chat.domain.ChatErrorCode;
import com.u1_2.effect.chat.domain.ChatScope;
import com.u1_2.effect.chat.domain.ChatScopeType;
import com.u1_2.effect.chat.presentation.dto.ChatRequest;
import com.u1_2.effect.chat.presentation.dto.ChatResponse;
import com.u1_2.effect.chat.presentation.dto.ChatResponse.Evidence;
import com.u1_2.effect.chat.presentation.dto.ChatThreadResponse;
import com.u1_2.effect.config.ai.AiClient;
import com.u1_2.effect.config.ai.AiClient.ChatReply;
import com.u1_2.effect.config.ai.AiClient.ChatScopeRef;
import com.u1_2.effect.config.ai.AiClient.ChatTurn;
import com.u1_2.effect.config.ai.AiUnavailableException;
import com.u1_2.effect.config.jwt.AuthUser;
import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.global.exception.CommonErrorCode;
import com.u1_2.effect.global.time.KoreaTime;
import com.u1_2.effect.user.application.RoleAccess;
import com.u1_2.effect.user.domain.Role;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 에이전트 패널 채팅. 문맥 준비 → AI 호출 → 저장 순서이고 AI를 기다리는 동안 DB 연결을 잡지 않음.
 * 상태는 모두 DB·Redis에 있어 Spring 인스턴스를 여러 개로 늘려도 같은 대화를 이어 감.
 */
@Slf4j
@Service
public class ChatService {

    // CS 화면에는 원인·설비 근거를 보이지 않음. AI도 거르지만 Spring에서 한 번 더 막음
    private static final Set<String> CS_EVIDENCE = Set.of("SIMILAR_CASE", "RMA");

    private final Map<ChatScopeType, ChatContextProvider> providers = new EnumMap<>(ChatScopeType.class);
    private final ChatThreadService threads;
    private final ChatRateLimiter rateLimiter;
    private final ObjectProvider<AiClient> aiClients;
    private final Clock appClock;
    private final int historySize;

    public ChatService(List<ChatContextProvider> providers, ChatThreadService threads, ChatRateLimiter rateLimiter,
                       ObjectProvider<AiClient> aiClients, Clock appClock,
                       @Value("${app.chat.history-size:10}") int historySize) {
        providers.forEach(provider -> this.providers.put(provider.type(), provider));
        this.threads = threads;
        this.rateLimiter = rateLimiter;
        this.aiClients = aiClients;
        this.appClock = appClock;
        this.historySize = historySize;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ChatResponse chat(ChatRequest request, AuthUser user) {
        RoleAccess.requireMember(user.role());
        if (!rateLimiter.tryAcquire(user.id())) {
            throw new ApiException(ChatErrorCode.CHAT_RATE_LIMITED);
        }
        var scope = request.scope().toScope();
        var askedAt = OffsetDateTime.now(appClock);
        // 권한·존재 확인이 먼저라 없는 RMA나 권한 없는 패턴으로는 대화가 만들어지지 않음
        var context = providers.get(scope.type()).load(scope, user.role());

        boolean newThread = request.threadId() == null;
        UUID threadId = newThread ? UUID.randomUUID() : request.threadId();
        List<ChatTurn> history = newThread ? List.of() : threads.history(threadId, user.id(), scope, historySize);

        AiClient aiClient = aiClients.getIfAvailable();
        if (aiClient == null) {
            throw new AiUnavailableException();
        }
        ChatReply reply = aiClient.chat(new AiClient.ChatRequest(threadId.toString(), user.role().name(),
                new ChatScopeRef(scope.type().name(), scope.externalId()), request.message(), history, context));

        var answer = answer(reply, user.role());
        var answeredAt = OffsetDateTime.now(appClock);
        threads.saveExchange(user.id(), scope, threadId, newThread, request.message(), answer, askedAt, answeredAt);
        log.info("chat answered. user={}, scope={}:{}, thread={}, status={}, model={}", user.id(), scope.type(),
                scope.id(), threadId, reply.status(), reply.modelVersion());
        return new ChatResponse(threadId, answer.status(), answer.text(), answer.evidence(),
                KoreaTime.inSeoul(answeredAt));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Optional<ChatThreadResponse> openThread(ChatScopeType type, String scopeId, AuthUser user) {
        RoleAccess.requireMember(user.role());
        if (type.requiresId() && (scopeId == null || scopeId.isBlank())) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT);
        }
        return threads.findOpen(user.id(), type, ChatScope.of(type, scopeId, null, null).id());
    }

    private Answer answer(ChatReply reply, Role role) {
        var evidence = Optional.ofNullable(reply.evidence()).orElse(List.of()).stream().filter(Objects::nonNull)
                .filter(e -> role == Role.QUALITY || CS_EVIDENCE.contains(e.type()))
                .limit(5).map(e -> new Evidence(e.type(), e.rmaId(), e.label())).toList();
        return new Answer(reply.answer(), reply.status(), evidence, reply.modelVersion());
    }
}
