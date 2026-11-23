package com.u1_2.effect.dashboard.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.u1_2.effect.dashboard.domain.DashboardWidget;
import java.util.List;

public record DashboardLayoutResponse(
        List<DashboardWidget> layout,
        @JsonProperty("default") List<DashboardWidget> defaultLayout
) {
}
