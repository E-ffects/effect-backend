package com.u1_2.effect.chat.application.context;

import com.u1_2.effect.chat.domain.ChatScope;
import com.u1_2.effect.chat.domain.ChatScopeType;
import com.u1_2.effect.user.domain.Role;
import tools.jackson.databind.JsonNode;

/**
 * 범위마다 AI에 넘길 문맥을 만듦. 범위를 추가할 때는 구현체만 추가함.
 * 권한·존재 확인과 역할별 필터는 화면과 같은 조회 서비스를 거쳐 화면에서 못 보는 정보가 채팅에도 들어가지 않게 함.
 */
public interface ChatContextProvider {

    ChatScopeType type();

    JsonNode load(ChatScope scope, Role role);
}
