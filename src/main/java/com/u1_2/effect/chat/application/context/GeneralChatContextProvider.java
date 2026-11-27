package com.u1_2.effect.chat.application.context;

import com.u1_2.effect.chat.domain.ChatScope;
import com.u1_2.effect.chat.domain.ChatScopeType;
import com.u1_2.effect.user.application.RoleAccess;
import com.u1_2.effect.user.domain.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 그 외 화면: 어떤 질문을 받을지 AI 담당과 정하기 전이라 문맥은 빈 객체로 보냄 */
@Component
@RequiredArgsConstructor
public class GeneralChatContextProvider implements ChatContextProvider {

    private final JsonMapper jsonMapper;

    @Override
    public ChatScopeType type() {
        return ChatScopeType.GENERAL;
    }

    @Override
    public JsonNode load(ChatScope scope, Role role) {
        RoleAccess.requireMember(role);
        return jsonMapper.createObjectNode();
    }
}
