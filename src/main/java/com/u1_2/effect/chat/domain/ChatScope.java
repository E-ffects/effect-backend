package com.u1_2.effect.chat.domain;

import java.time.LocalDate;

/**
 * 대화 범위. 대화는 (사용자, type, id)로 나뉘고 from·to는 패턴 집계 기간이라 대화를 나누지 않음.
 */
public record ChatScope(ChatScopeType type, String id, LocalDate from, LocalDate to) {
}
