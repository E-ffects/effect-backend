package com.u1_2.effect.user.presentation;

import com.u1_2.effect.config.jwt.RefreshTokenCookie;
import com.u1_2.effect.global.dto.ResponseDto;
import com.u1_2.effect.global.dto.SuccessMessage;
import com.u1_2.effect.user.application.AuthService;
import com.u1_2.effect.user.application.AuthService.LoginResult;
import com.u1_2.effect.user.presentation.dto.CheckUsernameResponse;
import com.u1_2.effect.user.presentation.dto.LoginRequest;
import com.u1_2.effect.user.presentation.dto.LoginResponse;
import com.u1_2.effect.user.presentation.dto.SignupRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "회원가입·로그인")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookie refreshTokenCookie;

    @Operation(summary = "회원가입")
    @PostMapping("/signup")
    public ResponseEntity<ResponseDto<Void>> signup(@Valid @RequestBody SignupRequest request) {
        authService.signup(request);
        return ResponseEntity.ok(ResponseDto.ofSuccess(SuccessMessage.CREATE_SUCCESS));
    }

    @Operation(summary = "아이디 중복 확인")
    @GetMapping("/check-username")
    public ResponseEntity<ResponseDto<CheckUsernameResponse>> checkUsername(@RequestParam @NotBlank String username) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(
                new CheckUsernameResponse(authService.isUsernameAvailable(username))));
    }

    @Operation(summary = "로그인", description = "Access Token은 본문 token, Refresh Token은 HttpOnly 쿠키(refresh_token)로 전달된다.")
    @PostMapping("/login")
    public ResponseEntity<ResponseDto<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResult result = authService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.create(result.refreshToken()).toString())
                .body(ResponseDto.ofSuccess(LoginResponse.of(result.accessToken(), result.user())));
    }
}
