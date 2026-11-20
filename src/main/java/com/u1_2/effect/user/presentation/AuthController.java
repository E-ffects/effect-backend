package com.u1_2.effect.user.presentation;

import com.u1_2.effect.config.jwt.AuthUser;
import com.u1_2.effect.config.jwt.RefreshTokenCookie;
import com.u1_2.effect.global.annotation.CurrentUser;
import com.u1_2.effect.global.dto.ResponseDto;
import com.u1_2.effect.global.dto.SuccessMessage;
import com.u1_2.effect.user.application.AuthService;
import com.u1_2.effect.user.application.AuthService.LoginResult;
import com.u1_2.effect.user.application.AuthService.ReissueResult;
import com.u1_2.effect.user.presentation.dto.CheckUsernameResponse;
import com.u1_2.effect.user.presentation.dto.CurrentUserResponse;
import com.u1_2.effect.user.presentation.dto.LoginRequest;
import com.u1_2.effect.user.presentation.dto.LoginResponse;
import com.u1_2.effect.user.presentation.dto.ReissueResponse;
import com.u1_2.effect.user.presentation.dto.SignupRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "회원가입·로그인·현재 사용자 조회·토큰 재발급·로그아웃")
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

    @Operation(summary = "현재 로그인한 사용자 조회")
    @GetMapping("/me")
    public ResponseEntity<ResponseDto<CurrentUserResponse>> me(@CurrentUser AuthUser authUser) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(
                CurrentUserResponse.from(authService.getCurrentUser(authUser.id()))));
    }

    @Operation(summary = "로그인", description = "Access Token은 본문 token, Refresh Token은 HttpOnly 쿠키(refresh_token)로 전달된다.")
    @PostMapping("/login")
    public ResponseEntity<ResponseDto<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResult result = authService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.create(result.refreshToken()).toString())
                .body(ResponseDto.ofSuccess(LoginResponse.of(result.accessToken(), result.user())));
    }

    @Operation(summary = "토큰 재발급", description = "refresh_token 쿠키로 Access Token을 재발급한다. Refresh Token도 함께 교체된다.")
    @PostMapping("/reissue")
    public ResponseEntity<ResponseDto<ReissueResponse>> reissue(
            @CookieValue(name = RefreshTokenCookie.NAME, required = false) String refreshToken) {
        ReissueResult result = authService.reissue(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.create(result.refreshToken()).toString())
                .body(ResponseDto.ofSuccess(new ReissueResponse(result.accessToken())));
    }

    @Operation(summary = "로그아웃", description = "저장된 Refresh Token을 삭제하고 쿠키를 만료시킨다. 토큰이 없거나 만료돼도 200.")
    @PostMapping("/logout")
    public ResponseEntity<ResponseDto<Void>> logout(
            @CookieValue(name = RefreshTokenCookie.NAME, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.expire().toString())
                .body(ResponseDto.ofSuccess(SuccessMessage.OPERATION_SUCCESS));
    }
}
