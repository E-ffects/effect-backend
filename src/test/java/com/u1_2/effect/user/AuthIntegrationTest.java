package com.u1_2.effect.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.u1_2.effect.TestcontainersConfiguration;
import com.u1_2.effect.config.jwt.JwtProperties;
import com.u1_2.effect.config.jwt.JwtProvider;
import com.u1_2.effect.config.jwt.RefreshTokenCookie;
import com.u1_2.effect.config.jwt.RefreshTokenRepository;
import com.u1_2.effect.user.domain.Role;
import com.u1_2.effect.user.domain.UserRepository;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuthIntegrationTest {

    private static final String SIGNUP_BODY = """
            {"name":"홍길동","username":"hong","password":"Passw0rd!","role":"QUALITY"}
            """;

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired StringRedisTemplate redisTemplate;
    @Autowired JwtProvider jwtProvider;
    @Autowired JwtProperties jwtProperties;
    @Autowired RefreshTokenRepository refreshTokens;

    @BeforeEach
    void clean() {
        userRepository.deleteAll();
    }

    @Test
    void 같은_세션의_동시_교체는_한_번만_성공하고_이전_토큰은_현재_세션을_삭제하지_않는다() throws Exception {
        long accountId = Long.MAX_VALUE;
        refreshTokens.save(accountId, "original");
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                return refreshTokens.rotate(accountId, "original", "first");
            });
            var second = executor.submit(() -> {
                start.await();
                return refreshTokens.rotate(accountId, "original", "second");
            });
            start.countDown();
            boolean firstWon = first.get(5, TimeUnit.SECONDS);
            assertThat(second.get(5, TimeUnit.SECONDS)).isNotEqualTo(firstWon);
            assertThat(refreshTokens.deleteIfCurrent(accountId, "original")).isFalse();
            assertThat(refreshTokens.deleteIfCurrent(accountId, firstWon ? "first" : "second")).isTrue();
        } finally {
            redisTemplate.delete("refresh:" + accountId);
        }
    }

    @Test
    void 회원가입_후_로그인하면_토큰과_사용자_정보_그리고_Refresh_쿠키를_받는다() throws Exception {
        signup().andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("등록이 완료되었습니다."));

        String body = login("hong", "Passw0rd!")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.content.token").isNotEmpty())
                .andExpect(jsonPath("$.content.user.name").value("홍길동"))
                .andExpect(jsonPath("$.content.user.role").value("QUALITY"))
                .andExpect(jsonPath("$.content.user.role_label").value("품질관리자"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, Matchers.allOf(
                        Matchers.startsWith("refresh_token="),
                        Matchers.containsString("Path=/api/auth"),
                        Matchers.containsString("HttpOnly"),
                        Matchers.containsString("SameSite=Strict"))))
                .andReturn().getResponse().getContentAsString();

        // 비밀번호는 해시로 저장
        var user = userRepository.findByUsername("hong").orElseThrow();
        assertThat(user.getPassword()).startsWith("$2").doesNotContain("Passw0rd!");
        assertThat(user.getCreatedAt()).isNotNull();

        // Refresh Token은 Redis에 해시로, TTL과 함께 저장
        String stored = redisTemplate.opsForValue().get("refresh:" + user.getId());
        assertThat(stored).hasSize(64);
        assertThat(redisTemplate.getExpire("refresh:" + user.getId())).isPositive();

        // 발급된 Access Token으로 인증 통과
        String token = JsonPath.read(body, "$.content.token");
        mockMvc.perform(get("/nothing").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void 아이디가_중복이면_409() throws Exception {
        signup();
        signup().andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_USERNAME"));
    }

    @Test
    void 비밀번호_규칙을_어기면_400() throws Exception {
        mockMvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"홍길동","username":"hong","password":"short","role":"QUALITY"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 아이디_중복_확인() throws Exception {
        mockMvc.perform(get("/auth/check-username").param("username", "hong")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
                .andExpect(jsonPath("$.content.available").value(true));
        signup();
        mockMvc.perform(get("/auth/check-username").param("username", "hong"))
                .andExpect(jsonPath("$.content.available").value(false));
    }

    @Test
    void 로그인한_사용자의_정보를_조회한다() throws Exception {
        signup().andExpect(status().isOk());
        String body = login("hong", "Passw0rd!").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String accessToken = JsonPath.read(body, "$.content.token");
        Long userId = userRepository.findByUsername("hong").orElseThrow().getId();

        mockMvc.perform(get("/api/auth/me").contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.content.id").value(Matchers.is(userId), Long.class))
                .andExpect(jsonPath("$.content.username").value("hong"))
                .andExpect(jsonPath("$.content.name").value("홍길동"))
                .andExpect(jsonPath("$.content.role").value("QUALITY"))
                .andExpect(jsonPath("$.content.role_label").value("품질관리자"))
                .andExpect(jsonPath("$.content.password").doesNotExist());
    }

    @Test
    void 현재_사용자_조회에는_Access_Token이_필요하다() throws Exception {
        mockMvc.perform(get("/api/auth/me").contextPath("/api"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        String refreshToken = jwtProvider.createRefreshToken(1L, Role.CS);
        mockMvc.perform(get("/api/auth/me").contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void 비밀번호가_틀리거나_아이디가_없으면_같은_401() throws Exception {
        signup();
        login("hong", "Wrong0000!").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        login("nobody", "Passw0rd!").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void 토큰_없이_보호된_경로에_접근하면_401_UNAUTHORIZED() throws Exception {
        mockMvc.perform(get("/rmas"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 위조된_토큰은_401_INVALID_TOKEN() throws Exception {
        mockMvc.perform(get("/rmas").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void Refresh_Token을_Access_Token처럼_쓰면_401_INVALID_TOKEN() throws Exception {
        String refresh = jwtProvider.createRefreshToken(1L, Role.CS);
        mockMvc.perform(get("/rmas").header(HttpHeaders.AUTHORIZATION, "Bearer " + refresh))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void 재발급하면_새_Access_Token과_새_Refresh_쿠키를_받고_이전_Refresh는_무효() throws Exception {
        signup();
        String oldRefresh = refreshCookie(login("hong", "Passw0rd!"));

        ResultActions reissued = reissue(oldRefresh)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.token").isNotEmpty());
        String newRefresh = refreshCookie(reissued);
        assertThat(newRefresh).isNotEqualTo(oldRefresh);

        // 새 Access Token으로 인증 통과
        String token = JsonPath.read(reissued.andReturn().getResponse().getContentAsString(), "$.content.token");
        mockMvc.perform(get("/nothing").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());

        // 교체된 이전 Refresh는 거절, 새 Refresh는 사용 가능
        reissue(oldRefresh).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        reissue(newRefresh).andExpect(status().isOk());
    }

    @Test
    void 만료된_Access_Token이_헤더에_있어도_재발급된다() throws Exception {
        signup();
        String refresh = refreshCookie(login("hong", "Passw0rd!"));

        mockMvc.perform(post("/auth/reissue")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredAccessToken())
                        .cookie(new Cookie(RefreshTokenCookie.NAME, refresh)))
                .andExpect(status().isOk());
    }

    @Test
    void Refresh_쿠키_없이_재발급하면_401_UNAUTHORIZED() throws Exception {
        mockMvc.perform(post("/auth/reissue"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void Access_Token으로_재발급하면_401_INVALID_TOKEN() throws Exception {
        String access = jwtProvider.createAccessToken(1L, Role.CS);
        reissue(access).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void 로그아웃하면_Redis에서_삭제되고_쿠키가_만료되며_재발급할_수_없다() throws Exception {
        signup();
        String refresh = refreshCookie(login("hong", "Passw0rd!"));
        Long userId = userRepository.findByUsername("hong").orElseThrow().getId();

        mockMvc.perform(post("/auth/logout").cookie(new Cookie(RefreshTokenCookie.NAME, refresh)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, Matchers.allOf(
                        Matchers.startsWith("refresh_token=;"),
                        Matchers.containsString("Max-Age=0"))));

        assertThat(redisTemplate.hasKey("refresh:" + userId)).isFalse();
        reissue(refresh).andExpect(status().isUnauthorized());
    }

    @Test
    void 쿠키_없이_로그아웃해도_200() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, Matchers.containsString("Max-Age=0")));
    }

    @Test
    void 이전_Refresh로_로그아웃해도_새_세션은_유지된다() throws Exception {
        signup();
        String oldRefresh = refreshCookie(login("hong", "Passw0rd!"));
        String newRefresh = refreshCookie(login("hong", "Passw0rd!"));

        mockMvc.perform(post("/auth/logout").cookie(new Cookie(RefreshTokenCookie.NAME, oldRefresh)))
                .andExpect(status().isOk());
        reissue(newRefresh).andExpect(status().isOk());
    }

    private ResultActions reissue(String refreshToken) throws Exception {
        return mockMvc.perform(post("/auth/reissue").cookie(new Cookie(RefreshTokenCookie.NAME, refreshToken)));
    }

    private String refreshCookie(ResultActions result) {
        Cookie cookie = result.andReturn().getResponse().getCookie(RefreshTokenCookie.NAME);
        assertThat(cookie).isNotNull();
        return cookie.getValue();
    }

    private String expiredAccessToken() {
        JwtProperties expired = new JwtProperties(
                jwtProperties.secret(), Duration.ofSeconds(-1), jwtProperties.refreshTokenTtl(), false);
        return new JwtProvider(expired).createAccessToken(1L, Role.CS);
    }

    private ResultActions signup() throws Exception {
        return mockMvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content(SIGNUP_BODY));
    }

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)));
    }
}
