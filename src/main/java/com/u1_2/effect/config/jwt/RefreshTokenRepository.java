package com.u1_2.effect.config.jwt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

/**
 * Refresh Token 저장소.
 * key = refresh:{userId}, value = 토큰 SHA-256 해시, TTL = Refresh 만료 시간.
 * 계정당 하나만 유지하므로 다른 곳에서 로그인하면 이전 Refresh Token은 무효가 된다.
 */
@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private static final String KEY_PREFIX = "refresh:";

    // 확인과 변경이 같은 Redis 명령 흐름에서 실행되어 그 사이에 새 세션을 덮거나 지우지 않음
    private static final RedisScript<Long> UPDATE_CURRENT = RedisScript.of("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then
                return 0
            end
            if #ARGV == 1 then
                return redis.call('DEL', KEYS[1])
            end
            redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties properties;

    public void save(Long userId, String refreshToken) {
        redisTemplate.opsForValue().set(key(userId), hash(refreshToken), properties.refreshTokenTtl());
    }

    public boolean rotate(Long userId, String currentToken, String newToken) {
        return Long.valueOf(1).equals(redisTemplate.execute(UPDATE_CURRENT, List.of(key(userId)),
                hash(currentToken), hash(newToken), Long.toString(properties.refreshTokenTtl().toMillis())));
    }

    public boolean deleteIfCurrent(Long userId, String currentToken) {
        return Long.valueOf(1).equals(redisTemplate.execute(UPDATE_CURRENT, List.of(key(userId)), hash(currentToken)));
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }
}
