package com.u1_2.effect.chat.application;

/** 사용자당 채팅 요청 제한. 인스턴스가 여러 개여도 같은 한도가 적용되도록 공유 저장소에서 셈 */
public interface ChatRateLimiter {

    boolean tryAcquire(long userId);
}
