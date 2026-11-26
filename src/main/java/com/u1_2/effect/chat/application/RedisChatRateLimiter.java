package com.u1_2.effect.chat.application;

import java.time.Clock;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * 1분 고정 창으로 셈. key = chat:rate:{userId}:{분}, TTL 2분.
 * Redis 장애 때는 제한 없이 통과시킴(채팅 자체를 막지 않음). 비용 상한은 AI 서버 쪽 제한이 맡음.
 */
@Slf4j
@Component
public class RedisChatRateLimiter implements ChatRateLimiter {

    private static final String KEY_PREFIX = "chat:rate:";
    // 증가와 만료 설정을 한 명령 흐름에서 실행해 만료 없는 키가 남지 않게 함
    private static final RedisScript<Long> INCREMENT = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('EXPIRE', KEYS[1], 120)
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final Clock appClock;
    private final int perMinute;

    public RedisChatRateLimiter(StringRedisTemplate redisTemplate, Clock appClock,
                                @Value("${app.chat.rate-limit-per-minute:10}") int perMinute) {
        this.redisTemplate = redisTemplate;
        this.appClock = appClock;
        this.perMinute = perMinute;
    }

    @Override
    public boolean tryAcquire(long userId) {
        long minute = appClock.millis() / 60_000;
        try {
            Long count = redisTemplate.execute(INCREMENT, List.of(KEY_PREFIX + userId + ":" + minute));
            return count == null || count <= perMinute;
        } catch (DataAccessException e) {
            log.warn("채팅 요청 제한을 확인하지 못해 통과시킴. cause={}", e.getClass().getSimpleName());
            return true;
        }
    }
}
