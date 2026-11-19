package com.u1_2.effect.user.application;

import com.u1_2.effect.config.jwt.JwtProvider;
import com.u1_2.effect.config.jwt.JwtProvider.TokenClaims;
import com.u1_2.effect.config.jwt.RefreshTokenRepository;
import com.u1_2.effect.config.jwt.TokenType;
import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.global.exception.AuthErrorCode;
import com.u1_2.effect.user.domain.User;
import com.u1_2.effect.user.domain.UserErrorCode;
import com.u1_2.effect.user.domain.UserRepository;
import com.u1_2.effect.user.presentation.dto.LoginRequest;
import com.u1_2.effect.user.presentation.dto.SignupRequest;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public void signup(SignupRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ApiException(UserErrorCode.DUPLICATE_USERNAME);
        }
        User user = User.create(
                request.username(),
                passwordEncoder.encode(request.password()),
                request.name(),
                request.role());
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 아이디로 가입한 경우
            throw new ApiException(UserErrorCode.DUPLICATE_USERNAME);
        }
        log.info("Signup: userId={}, role={}", user.getId(), user.getRole());
    }

    public boolean isUsernameAvailable(String username) {
        return !userRepository.existsByUsername(username);
    }

    public LoginResult login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPassword()))
                .orElseThrow(() -> new ApiException(AuthErrorCode.INVALID_CREDENTIALS));

        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getRole());
        String refreshToken = jwtProvider.createRefreshToken(user.getId(), user.getRole());
        refreshTokenRepository.save(user.getId(), refreshToken);

        log.info("Login: userId={}", user.getId());
        return new LoginResult(accessToken, refreshToken, user);
    }

    /**
     * Refresh Token으로 Access·Refresh Token을 새로 발급한다.
     */
    public ReissueResult reissue(String refreshToken) {
        TokenClaims claims = parseRefreshToken(refreshToken);
        if (!refreshTokenRepository.matches(claims.userId(), refreshToken)) {
            log.warn("Refresh token mismatch: userId={}", claims.userId());
            throw new ApiException(AuthErrorCode.INVALID_TOKEN);
        }
        // 권한이 바뀌었을 수 있으므로 토큰이 아니라 DB의 현재 값으로 발급
        User user = userRepository.findById(claims.userId())
                .orElseThrow(() -> new ApiException(AuthErrorCode.INVALID_TOKEN));

        String newAccessToken = jwtProvider.createAccessToken(user.getId(), user.getRole());
        String newRefreshToken = jwtProvider.createRefreshToken(user.getId(), user.getRole());
        refreshTokenRepository.save(user.getId(), newRefreshToken);

        log.info("Reissue: userId={}", user.getId());
        return new ReissueResult(newAccessToken, newRefreshToken);
    }

    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        try {
            TokenClaims claims = jwtProvider.parse(refreshToken);
            // 다른 곳에서 새로 로그인한 세션은 건드리지 않도록 저장된 토큰과 같을 때만 삭제
            if (claims.type() == TokenType.REFRESH && refreshTokenRepository.matches(claims.userId(), refreshToken)) {
                refreshTokenRepository.delete(claims.userId());
                log.info("Logout: userId={}", claims.userId());
            }
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Logout with invalid refresh token: reason={}", e.getClass().getSimpleName());
        }
    }

    private TokenClaims parseRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ApiException(AuthErrorCode.UNAUTHORIZED);
        }
        TokenClaims claims;
        try {
            claims = jwtProvider.parse(refreshToken);
        } catch (ExpiredJwtException e) {
            throw new ApiException(AuthErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new ApiException(AuthErrorCode.INVALID_TOKEN);
        }
        if (claims.type() != TokenType.REFRESH) {
            throw new ApiException(AuthErrorCode.INVALID_TOKEN);
        }
        return claims;
    }

    public record LoginResult(String accessToken, String refreshToken, User user) {
    }

    public record ReissueResult(String accessToken, String refreshToken) {
    }
}
