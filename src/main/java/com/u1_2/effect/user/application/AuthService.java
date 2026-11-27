package com.u1_2.effect.user.application;

import com.u1_2.effect.config.jwt.JwtProvider;
import com.u1_2.effect.config.jwt.JwtProvider.TokenClaims;
import com.u1_2.effect.config.jwt.RefreshTokenRepository;
import com.u1_2.effect.config.jwt.TokenType;
import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.global.exception.AuthErrorCode;
import com.u1_2.effect.user.domain.ApprovalStatus;
import com.u1_2.effect.user.domain.User;
import com.u1_2.effect.user.domain.UserErrorCode;
import com.u1_2.effect.user.domain.UserRepository;
import com.u1_2.effect.user.presentation.dto.LoginRequest;
import com.u1_2.effect.user.presentation.dto.SignupRequest;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.util.List;
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

    public User getCurrentUser(Long userId) {
        // 토큰에서 확인한 사용자 번호로 최신 프로필 정보를 조회함
        return userRepository.findByIdOrThrow(userId, AuthErrorCode.INVALID_TOKEN);
    }

    public LoginResult login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPassword()))
                .orElseThrow(() -> new ApiException(AuthErrorCode.INVALID_CREDENTIALS));

        requireApproved(user);

        var tokens = createSessionTokens(user);
        refreshTokenRepository.save(user.getId(), tokens.refreshToken());

        log.info("Login: userId={}", user.getId());
        return new LoginResult(tokens, user);
    }

    /**
     * Refresh Token으로 Access·Refresh Token을 새로 발급한다.
     */
    public SessionTokens reissue(String refreshToken) {
        TokenClaims claims = parseRefreshToken(refreshToken);
        // 권한이 바뀌었을 수 있으므로 DB의 현재 권한으로 토큰을 만든 뒤 기존 세션과 일치할 때만 교체함
        User user = getCurrentUser(claims.userId());
        requireApproved(user);
        var tokens = createSessionTokens(user);
        if (!refreshTokenRepository.rotate(user.getId(), refreshToken, tokens.refreshToken())) {
            log.warn("Refresh token mismatch: userId={}", claims.userId());
            throw new ApiException(AuthErrorCode.INVALID_TOKEN);
        }

        log.info("Reissue: userId={}", user.getId());
        return tokens;
    }

    public List<User> listUsersForAdmin(Long adminUserId, ApprovalStatus status) {
        requireAdmin(adminUserId);
        return status == null
                ? userRepository.findAllByOrderByCreatedAtDesc()
                : userRepository.findByApprovalStatusOrderByCreatedAtDesc(status);
    }

    @Transactional
    public User approveUser(Long adminUserId, Long targetUserId) {
        requireAdmin(adminUserId);
        User target = userRepository.findByIdOrThrow(targetUserId, UserErrorCode.USER_NOT_FOUND);
        target.approve();
        log.info("User approved: adminId={}, targetUserId={}", adminUserId, targetUserId);
        return target;
    }

    @Transactional
    public User rejectUser(Long adminUserId, Long targetUserId) {
        requireAdmin(adminUserId);
        User target = userRepository.findByIdOrThrow(targetUserId, UserErrorCode.USER_NOT_FOUND);
        target.reject();
        log.info("User rejected: adminId={}, targetUserId={}", adminUserId, targetUserId);
        return target;
    }

    private void requireAdmin(Long userId) {
        User user = getCurrentUser(userId);
        if (!user.isAdmin()) {
            throw new ApiException(UserErrorCode.ADMIN_REQUIRED);
        }
    }

    private void requireApproved(User user) {
        if (user.getApprovalStatus() == ApprovalStatus.PENDING) {
            throw new ApiException(UserErrorCode.APPROVAL_PENDING);
        }
        if (user.getApprovalStatus() == ApprovalStatus.REJECTED) {
            throw new ApiException(UserErrorCode.APPROVAL_REJECTED);
        }
    }

    private SessionTokens createSessionTokens(User user) {
        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getRole());
        String refreshToken = jwtProvider.createRefreshToken(user.getId(), user.getRole());
        return new SessionTokens(accessToken, refreshToken);
    }

    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        try {
            TokenClaims claims = jwtProvider.parse(refreshToken);
            // 다른 곳에서 새로 로그인한 세션은 건드리지 않도록 저장된 토큰과 같을 때만 삭제
            if (claims.type() == TokenType.REFRESH && refreshTokenRepository.deleteIfCurrent(claims.userId(), refreshToken)) {
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

    public record LoginResult(SessionTokens tokens, User user) {
    }

    public record SessionTokens(String accessToken, String refreshToken) {
    }
}
