package com.u1_2.effect.config.jwt;

import com.u1_2.effect.user.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * JWT 발급·검증.
 * 클레임: sub = userId, role = QUALITY|CS, type = ACCESS|REFRESH
 */
@Component
public class JwtProvider {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TYPE = "type";

    private final SecretKey key;
    private final JwtProperties properties;

    public JwtProvider(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Long userId, Role role) {
        return create(userId, role, TokenType.ACCESS, properties.accessTokenTtl());
    }

    public String createRefreshToken(Long userId, Role role) {
        return create(userId, role, TokenType.REFRESH, properties.refreshTokenTtl());
    }

    /**
     * 서명·만료를 검증하고 내용을 돌려준다. 토큰은 한 번만 파싱한다.
     *
     * @throws io.jsonwebtoken.ExpiredJwtException 만료
     * @throws JwtException                        위조·형식 오류
     */
    public TokenClaims parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        try {
            return new TokenClaims(
                    Long.valueOf(claims.getSubject()),
                    Role.valueOf(claims.get(CLAIM_ROLE, String.class)),
                    TokenType.valueOf(claims.get(CLAIM_TYPE, String.class)));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new JwtException("필수 클레임이 없거나 잘못되었습니다.", e);
        }
    }

    private String create(Long userId, Role role, TokenType type, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .claim(CLAIM_ROLE, role.name())
                .claim(CLAIM_TYPE, type.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    public record TokenClaims(Long userId, Role role, TokenType type) {
    }
}
