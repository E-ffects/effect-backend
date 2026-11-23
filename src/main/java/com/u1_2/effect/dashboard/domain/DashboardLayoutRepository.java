package com.u1_2.effect.dashboard.domain;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
@RequiredArgsConstructor
public class DashboardLayoutRepository {

    private final JdbcClient jdbcClient;
    private final JsonMapper jsonMapper;

    public List<DashboardWidget> findByUserId(long userId, String dashboard) {
        return jdbcClient.sql("""
                SELECT widgets::text FROM app.dashboard_layout
                WHERE user_id = :userId AND dashboard = :dashboard
                """).param("userId", userId).param("dashboard", dashboard).query(String.class).optional()
                .map(value -> List.of(jsonMapper.readValue(value, DashboardWidget[].class)))
                .orElseGet(List::of);
    }

    public void save(long userId, String dashboard, List<DashboardWidget> widgets) {
        // 동시 저장 시 사용자별 행 하나를 유지하고 전체 배치를 한 번에 바꿈
        jdbcClient.sql("""
                INSERT INTO app.dashboard_layout (user_id, dashboard, widgets)
                VALUES (:userId, :dashboard, CAST(:widgets AS jsonb))
                ON CONFLICT (user_id, dashboard)
                DO UPDATE SET widgets = EXCLUDED.widgets, updated_at = LOCALTIMESTAMP
                """).param("userId", userId).param("dashboard", dashboard)
                .param("widgets", jsonMapper.writeValueAsString(widgets)).update();
    }
}
