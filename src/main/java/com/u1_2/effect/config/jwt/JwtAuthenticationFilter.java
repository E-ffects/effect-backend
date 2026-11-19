package com.u1_2.effect.config.jwt;

import com.u1_2.effect.config.jwt.JwtProvider.TokenClaims;
import com.u1_2.effect.global.exception.AuthErrorCode;
import com.u1_2.effect.global.exception.ErrorResponseWriter;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authorization: Bearer {accessToken} 검증.
 * - 만료: 401 TOKEN_EXPIRED
 * - 위조·형식 오류·Refresh 토큰 사용: 401 INVALID_TOKEN
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTH_PATH_PREFIX = "/auth/";

    private final JwtProvider jwtProvider;
    private final ErrorResponseWriter errorResponseWriter;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith(AUTH_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        TokenClaims claims;
        try {
            claims = jwtProvider.parse(header.substring(BEARER_PREFIX.length()));
        } catch (ExpiredJwtException e) {
            log.debug("Access token expired: uri={}", request.getRequestURI());
            errorResponseWriter.write(response, AuthErrorCode.TOKEN_EXPIRED);
            return;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid access token: uri={}, reason={}", request.getRequestURI(), e.getClass().getSimpleName());
            errorResponseWriter.write(response, AuthErrorCode.INVALID_TOKEN);
            return;
        }

        if (claims.type() != TokenType.ACCESS) {
            log.warn("Non-access token used as access token: uri={}", request.getRequestURI());
            errorResponseWriter.write(response, AuthErrorCode.INVALID_TOKEN);
            return;
        }

        AuthUser principal = new AuthUser(claims.userId(), claims.role());
        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(claims.role().getAuthority())));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        chain.doFilter(request, response);
    }
}
