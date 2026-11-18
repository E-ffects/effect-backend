package com.u1_2.effect.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.u1_2.effect.TestcontainersConfiguration;
import com.u1_2.effect.config.jwt.JwtProvider;
import com.u1_2.effect.user.domain.Role;
import com.u1_2.effect.user.domain.UserRepository;
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

    @BeforeEach
    void clean() {
        userRepository.deleteAll();
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
        mockMvc.perform(get("/auth/check-username").param("username", "hong"))
                .andExpect(jsonPath("$.content.available").value(true));
        signup();
        mockMvc.perform(get("/auth/check-username").param("username", "hong"))
                .andExpect(jsonPath("$.content.available").value(false));
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

    private ResultActions signup() throws Exception {
        return mockMvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content(SIGNUP_BODY));
    }

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)));
    }
}
