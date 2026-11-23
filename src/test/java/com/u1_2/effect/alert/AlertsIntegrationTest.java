package com.u1_2.effect.alert;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.u1_2.effect.TestcontainersConfiguration;
import com.u1_2.effect.alert.domain.AlertSourceRepository;
import com.u1_2.effect.config.jwt.JwtProvider;
import com.u1_2.effect.user.domain.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
@Sql("/sql/alert-fixture.sql")
class AlertsIntegrationTest {

    private static final String ALERTS = "/api/alerts";
    private static final long USER = 91001;
    private static final long OTHER_USER = 91002;

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwt;
    @Autowired AlertSourceRepository sources;
    @Autowired JdbcClient jdbcClient;

    long criticalId;
    long suspectedId;

    @BeforeEach
    void 원본에서_알림을_생성한다() {
        sources.refresh();
        criticalId = id("고위험");
        suspectedId = id("재발 의심");
    }

    @Test
    void 원본을_반복_동기화해도_알림이_한번만_생성되고_필터와_커서를_지원한다() throws Exception {
        sources.refresh();
        mvc.perform(quality(get(ALERTS)).param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.items.length()").value(1))
                .andExpect(jsonPath("$.content.max_id").value(suspectedId))
                .andExpect(jsonPath("$.content.unread").value(2))
                .andExpect(jsonPath("$.content.items[0].rma_id").value("RMA-B"))
                .andExpect(jsonPath("$.content.items[0].recur_id").value("CA-02"))
                .andExpect(jsonPath("$.content.unread_by_type['고위험']").value(1));
        mvc.perform(quality(get(ALERTS)).param("after_id", Long.toString(criticalId)).param("type", "재발 의심"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.items.length()").value(1))
                .andExpect(jsonPath("$.content.items[0].id").value(suspectedId))
                .andExpect(jsonPath("$.content.items[0].created_at").value("2025-12-19T10:00:00+09:00"));
    }

    @Test
    void 읽음_즐겨찾기_숨김을_사용자별로_격리한다() throws Exception {
        mvc.perform(quality(post(ALERTS + "/" + criticalId + "/read")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.read").value(true));
        mvc.perform(json(quality(post(ALERTS + "/" + criticalId + "/star")), "{\"starred\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.starred").value(true))
                .andExpect(jsonPath("$.content.read").value(true));
        mvc.perform(quality(get(ALERTS)).param("starred", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.items.length()").value(1))
                .andExpect(jsonPath("$.content.starred_count").value(1));
        mvc.perform(quality(delete(ALERTS + "/" + criticalId))).andExpect(status().isOk());
        mvc.perform(quality(post(ALERTS + "/" + criticalId + "/read")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ALERT_NOT_FOUND"));
        mvc.perform(request(get(ALERTS), OTHER_USER, Role.QUALITY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.unread").value(2))
                .andExpect(jsonPath("$.content.starred_count").value(0));
    }

    @Test
    void 일괄_처리는_유효한_알림만_반영하고_품질_권한을_확인한다() throws Exception {
        mvc.perform(json(quality(post(ALERTS + "/bulk-read")), "{\"ids\":[%d,%d,999999]}".formatted(criticalId, criticalId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.updated").value(1));
        mvc.perform(quality(post(ALERTS + "/read-all"))).andExpect(status().isOk());
        mvc.perform(quality(get(ALERTS))).andExpect(status().isOk()).andExpect(jsonPath("$.content.unread").value(0));
        mvc.perform(json(quality(post(ALERTS + "/bulk-delete")), "{\"ids\":[%d]}".formatted(suspectedId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.deleted").value(1));
        mvc.perform(quality(get(ALERTS))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.items.length()").value(1))
                .andExpect(jsonPath("$.content.max_id").value(suspectedId));
        mvc.perform(get(ALERTS).contextPath("/api")).andExpect(status().isUnauthorized());
        mvc.perform(request(get(ALERTS), USER, Role.CS)).andExpect(status().isForbidden());
        mvc.perform(quality(get(ALERTS)).param("limit", "101")).andExpect(status().isBadRequest());
    }

    private long id(String type) {
        return jdbcClient.sql("SELECT id FROM app.alert WHERE type = :type").param("type", type).query(Long.class).single();
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder quality(MockHttpServletRequestBuilder request) {
        return request(request, USER, Role.QUALITY);
    }

    private MockHttpServletRequestBuilder request(MockHttpServletRequestBuilder request, long userId, Role role) {
        return request.contextPath("/api").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.createAccessToken(userId, role));
    }
}
