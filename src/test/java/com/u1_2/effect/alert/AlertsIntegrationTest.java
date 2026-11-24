package com.u1_2.effect.alert;

import static org.hamcrest.Matchers.containsInAnyOrder;
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

@SpringBootTest(properties = "app.clock.fixed=2025-12-31T09:00:00+09:00")
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
    void 기능_적용_후_저장한_접수만_처리_상태와_관계없이_품질_알림을_한번_만든다() throws Exception {
        jdbcClient.sql("""
                INSERT INTO pcb.rma_claim (rma_id, reported_part_no) VALUES ('RMA-NEW-INTAKE', 'PCB-NEW');
                INSERT INTO app.intake (rma_id, intake_state, created_by, created_at, updated_at, notify_quality_on_intake)
                VALUES ('RMA-A', 'ACCEPTED', 91001, '2025-12-17T09:00:00+09:00', CURRENT_TIMESTAMP, FALSE);
                INSERT INTO app.intake (rma_id, intake_state, created_by, created_at, updated_at)
                VALUES ('RMA-NEW-INTAKE', 'FAILED', 91001, '2025-12-20T01:00:00Z', CURRENT_TIMESTAMP);
                """).update();
        sources.refresh();
        long newIntakeId = id("신규 반품 접수");
        jdbcClient.sql("UPDATE app.intake SET intake_state = 'ACCEPTED' WHERE rma_id = 'RMA-NEW-INTAKE'").update();
        sources.refresh();

        mvc.perform(quality(get(ALERTS)).param("type", "신규 반품 접수").param("after_id", Long.toString(suspectedId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.items.length()").value(1))
                .andExpect(jsonPath("$.content.items[0].id").value(newIntakeId))
                .andExpect(jsonPath("$.content.items[0].rma_id").value("RMA-NEW-INTAKE"))
                .andExpect(jsonPath("$.content.items[0].target_label").value("PCB-NEW · RMA-NEW-INTAKE"))
                .andExpect(jsonPath("$.content.items[0].created_at").value("2025-12-20T10:00:00+09:00"))
                .andExpect(jsonPath("$.content.unread_by_type['신규 반품 접수']").value(1));
        mvc.perform(request(get(ALERTS), USER, Role.CS).param("type", "신규 반품 접수"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 급증은_서울_날짜의_두_기간을_비교하고_최신_분류로_접수를_한번만_센다() throws Exception {
        jdbcClient.sql("""
                INSERT INTO pcb.rma_claim (rma_id, reported_part_no, opened_at)
                VALUES ('SPIKE-OLD', 'PCB-S', '2025-11-01T23:59:59+09:00'),
                       ('SPIKE-BASE', 'PCB-S', '2025-11-02T00:00:00+09:00'),
                       ('SPIKE-1', 'PCB-S', '2025-12-01T15:00:00Z'),
                       ('SPIKE-2', 'PCB-S', '2025-12-04T00:00:00+09:00'),
                       ('SPIKE-3', 'PCB-S', '2025-12-15T00:00:00+09:00'),
                       ('SPIKE-4', 'PCB-S', '2025-12-31T08:00:00+09:00'),
                       ('SPIKE-FUTURE', 'PCB-S', '2025-12-31T11:00:00+09:00');
                INSERT INTO pcb.symptom_definition VALUES ('M1', 'MAJ', '전기적 불량'), ('M2', 'OTHER', '외관');
                INSERT INTO pcb.rma_item SELECT 'ITEM-' || rma_id, rma_id FROM pcb.rma_claim WHERE rma_id LIKE 'SPIKE-%';
                INSERT INTO pcb.symptom_assessment
                    SELECT 'A-' || rma_item_id, rma_item_id, 'M1', '2025-12-20T01:00:00+09:00'
                    FROM pcb.rma_item WHERE rma_id LIKE 'SPIKE-%';
                INSERT INTO pcb.symptom_assessment
                    VALUES ('B-old', 'ITEM-SPIKE-4', 'M2', '2025-12-19T01:00:00+09:00');
                """).update();
        sources.refresh();
        sources.refresh();

        mvc.perform(quality(get(ALERTS)).param("type", "급증"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.items.length()").value(1))
                .andExpect(jsonPath("$.content.items[0].rma_id").value("SPIKE-4"))
                .andExpect(jsonPath("$.content.items[0].reason").value("같은 품번과 불량 대분류(전기적 불량) 접수가 직전 30일보다 3건 증가함"))
                .andExpect(jsonPath("$.content.unread_by_type['급증']").value(1));
    }

    @Test
    void 반복_재발은_같은_조사의_서로_다른_확정_접수와_조사_번호를_사용한다() throws Exception {
        jdbcClient.sql("""
                INSERT INTO pcb.corrective_action VALUES ('CA-02', 'INV-01'), ('CA-03', 'INV-01');
                INSERT INTO pcb.rma_claim (rma_id, reported_part_no) VALUES ('RMA-NEW', 'PCB-N');
                INSERT INTO pcb.rma_item VALUES ('ITEM-N', 'RMA-NEW');
                INSERT INTO pcb.recurrence_review VALUES
                    ('REV-03', 'ITEM-B1', 'CA-02', 'RECURRENCE', '2025-12-20T00:00:00Z'),
                    ('REV-04', 'ITEM-B2', 'CA-03', 'RECURRENCE', '2025-12-21T00:00:00Z'),
                    ('REV-05', 'ITEM-N', 'CA-02', 'RECURRENCE', '2025-12-20T01:00:00Z');
                INSERT INTO pcb.investigation_claim VALUES ('INV-01', 'RMA-A');
                INSERT INTO app.rma_recurrence_decision (rma_id, decision, decided_by, decided_by_name, decided_at)
                VALUES ('RMA-NEW', 'NEW', 91001, '품질 A', '2025-12-21T00:00:00Z');
                """).update();
        sources.refresh();
        mvc.perform(quality(get(ALERTS)).param("type", "반복 재발"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.items").isEmpty());

        jdbcClient.sql("""
                INSERT INTO app.rma_recurrence_decision (rma_id, decision, decided_by, decided_by_name, decided_at)
                VALUES ('RMA-A', 'RECURRENCE', 91001, '품질 A', '2025-12-22T01:00:00Z')
                """).update();
        sources.refresh();
        sources.refresh();
        mvc.perform(quality(get(ALERTS)).param("type", "반복 재발"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.items.length()").value(1))
                .andExpect(jsonPath("$.content.items[0].rma_id").value("RMA-A"))
                .andExpect(jsonPath("$.content.items[0].pattern_id").value("INV-01"))
                .andExpect(jsonPath("$.content.items[0].created_at").value("2025-12-22T10:00:00+09:00"));
    }

    @Test
    void 확산형_재발을_확정한_접수에만_한번_알림을_생성한다() throws Exception {
        jdbcClient.sql("""
                INSERT INTO app.rma_recurrence_decision (rma_id, decision, decided_by, decided_by_name, decided_at)
                VALUES ('RMA-A', 'SPREAD_RECURRENCE', :user, '품질 A', '2025-12-20T01:00:00Z'),
                       ('RMA-B', 'RECURRENCE', :user, '품질 A', '2025-12-20T02:00:00Z')
                """).param("user", USER).update();
        sources.refresh();
        sources.refresh();

        mvc.perform(quality(get(ALERTS)).param("type", "확산형").param("after_id", Long.toString(suspectedId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.items.length()").value(1))
                .andExpect(jsonPath("$.content.items[0].rma_id").value("RMA-A"))
                .andExpect(jsonPath("$.content.items[0].created_at").value("2025-12-20T10:00:00+09:00"))
                .andExpect(jsonPath("$.content.unread_by_type['확산형']").value(1));
    }

    @Test
    void 종료된_분석과_판정_결과만_분석별로_한번_생성한다() throws Exception {
        jdbcClient.sql("""
                INSERT INTO app.ai_analysis (analysis_id, rma_id, mode, analysis_state, created_at, completed_at)
                VALUES ('ana-a', 'RMA-A', 'B', 'SUCCEEDED', CURRENT_TIMESTAMP, '2025-12-20T03:00:00Z'),
                       ('ana-b', 'RMA-B', 'B', 'PARTIAL', CURRENT_TIMESTAMP, '2025-12-20T04:00:00Z'),
                       ('ana-c', 'RMA-A', 'B', 'FAILED', CURRENT_TIMESTAMP, '2025-12-20T05:00:00Z'),
                       ('ana-running', 'RMA-A', 'B', 'RUNNING', CURRENT_TIMESTAMP, NULL)
                """).update();
        jdbcClient.sql("""
                INSERT INTO app.cause_decision (rma_id, analysis_id, analysis_level, approval_status, decided_at)
                VALUES ('RMA-A', 'ana-a', 'L3', 'APPROVED', '2025-12-21T03:00:00Z'),
                       ('RMA-B', 'ana-b', 'L3', 'REJECTED', '2025-12-21T04:00:00Z'),
                       ('RMA-A', 'ana-running', 'L3', 'WAITING', NULL)
                """).update();
        sources.refresh();
        sources.refresh();

        mvc.perform(quality(get(ALERTS)).param("type", "원인 판정 결과"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.items[*].reason", containsInAnyOrder("원인 후보가 승인됨", "원인 후보가 반려됨")))
                .andExpect(jsonPath("$.content.unread_by_type['원인 판정 결과']").value(2));
        mvc.perform(quality(get(ALERTS)).param("type", "분석 결과"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.items[*].reason",
                        containsInAnyOrder("AI 분석이 완료됨", "AI 분석이 일부 완료됨", "AI 분석에 실패함")))
                .andExpect(jsonPath("$.content.unread_by_type['분석 결과']").value(3));
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
