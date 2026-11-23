package com.u1_2.effect.config.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Refresh Token 쿠키.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenCookie {

    public static final String NAME = "refresh_token";
    private static final String PATH = "/api/auth";

    private final JwtProperties properties;

    public ResponseCookie create(String refreshToken) {
        return base(refreshToken).maxAge(properties.refreshTokenTtl()).build();
    }

    public ResponseCookie expire() {
        return base("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path(PATH);
    }
}
