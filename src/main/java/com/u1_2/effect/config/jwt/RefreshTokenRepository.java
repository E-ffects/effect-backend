package com.u1_2.effect.config.jwt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
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

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties properties;

    public void save(Long userId, String refreshToken) {
        redisTemplate.opsForValue().set(key(userId), hash(refreshToken), properties.refreshTokenTtl());
    }

    public boolean matches(Long userId, String refreshToken) {
        String stored = redisTemplate.opsForValue().get(key(userId));
        return stored != null && MessageDigest.isEqual(
                stored.getBytes(StandardCharsets.UTF_8), hash(refreshToken).getBytes(StandardCharsets.UTF_8));
    }

    public void delete(Long userId) {
        redisTemplate.delete(key(userId));
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
