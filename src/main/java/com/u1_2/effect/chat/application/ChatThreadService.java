package com.u1_2.effect.chat.application;

import com.u1_2.effect.chat.domain.ChatErrorCode;
import com.u1_2.effect.chat.domain.ChatRepository;
import com.u1_2.effect.chat.domain.ChatRepository.NewMessage;
import com.u1_2.effect.chat.domain.ChatScope;
import com.u1_2.effect.chat.presentation.dto.ChatResponse.Evidence;
import com.u1_2.effect.chat.presentation.dto.ChatResponse.Suggestion;
import com.u1_2.effect.config.ai.AiClient.ChatTurn;
import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.global.exception.CommonErrorCode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** 대화 저장·조회. AI 호출과 분리된 짧은 트랜잭션만 가짐 */
@Service
@RequiredArgsConstructor
public class ChatThreadService {

    private final ChatRepository repository;
    private final JsonMapper jsonMapper;

    /** 이어서 묻는 대화의 최근 메시지. 다른 사용자·다른 범위·닫힌 대화는 없는 대화로 취급함 */
    @Transactional(readOnly = true)
    public List<ChatTurn> history(UUID threadId, long userId, ChatScope scope, int size) {
        repository.findThread(threadId).filter(t -> t.belongsTo(userId, scope.type(), scope.id()))
                .orElseThrow(() -> new ApiException(ChatErrorCode.CHAT_THREAD_NOT_FOUND));
        return repository.findRecentMessages(threadId, size).stream()
                .map(m -> new ChatTurn(m.role(), m.text())).toList();
    }

    /**
     * 질문과 답변을 함께 저장함. 새 대화면 같은 범위의 이전 대화를 닫고 연다.
     * 다른 요청이 그 사이 같은 범위의 대화를 열었으면 그 대화도 닫고 한 번 더 시도함.
     */
    @Transactional
    public void saveExchange(long userId, ChatScope scope, UUID threadId, boolean newThread,
                             String question, Answer answer, OffsetDateTime askedAt, OffsetDateTime answeredAt) {
        if (newThread) {
            openThread(userId, scope, threadId, askedAt);
        }
        repository.insertMessage(threadId, new NewMessage("user", question, null, null, null, null, askedAt));
        repository.insertMessage(threadId, new NewMessage("assistant", answer.text(), answer.status(),
                jsonMapper.writeValueAsString(answer.evidence()), jsonMapper.writeValueAsString(answer.suggestions()),
                answer.modelVersion(), answeredAt));
        repository.touchThread(threadId, answeredAt);
    }

    private void openThread(long userId, ChatScope scope, UUID threadId, OffsetDateTime now) {
        for (int attempt = 0; attempt < 2; attempt++) {
            repository.closeOpenThreads(userId, scope.type(), scope.id(), now);
            if (repository.insertThread(threadId, userId, scope.type(), scope.id(), now)) {
                return;
            }
        }
        throw new ApiException(CommonErrorCode.INTERNAL_ERROR);
    }

    public record Answer(String text, String status, List<Evidence> evidence, List<Suggestion> suggestions,
                         String modelVersion) {
    }
}
