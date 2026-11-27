package com.u1_2.effect.chat.domain;

import java.time.LocalDate;

/**
 * 대화 범위. 대화는 (사용자, type, id)로 나뉘고 from·to는 패턴 집계 기간이라 대화를 나누지 않음.
 */
public record ChatScope(ChatScopeType type, String id, LocalDate from, LocalDate to) {

    /** GENERAL은 범위 ID가 없어 저장할 때 고정값을 씀. 그래서 사용자당 열린 GENERAL 대화가 하나가 됨 */
    public static final String GENERAL_ID = "-";

    public static ChatScope of(ChatScopeType type, String id, LocalDate from, LocalDate to) {
        if (!type.requiresId()) {
            return new ChatScope(type, GENERAL_ID, null, null);
        }
        return new ChatScope(type, id.trim(), from, to);
    }

    /** AI에 보내는 범위 ID. GENERAL은 null */
    public String externalId() {
        return type.requiresId() ? id : null;
    }
}
