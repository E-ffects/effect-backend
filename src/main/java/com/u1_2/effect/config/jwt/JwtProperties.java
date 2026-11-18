package com.u1_2.effect.config.jwt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param secret          HS256 서명 키
 * @param accessTokenTtl  Access Token 만료 시간
 * @param refreshTokenTtl Refresh Token 만료 시간
 * @param cookieSecure    Refresh 쿠키 Secure 여부
 */
@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32) String secret,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl,
        boolean cookieSecure
) {
}
