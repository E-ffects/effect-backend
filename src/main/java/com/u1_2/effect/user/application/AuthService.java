package com.u1_2.effect.user.application;

import com.u1_2.effect.config.jwt.JwtProvider;
import com.u1_2.effect.config.jwt.RefreshTokenRepository;
import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.global.exception.AuthErrorCode;
import com.u1_2.effect.user.domain.User;
import com.u1_2.effect.user.domain.UserErrorCode;
import com.u1_2.effect.user.domain.UserRepository;
import com.u1_2.effect.user.presentation.dto.LoginRequest;
import com.u1_2.effect.user.presentation.dto.SignupRequest;
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

    public record LoginResult(String accessToken, String refreshToken, User user) {
    }
}
