package com.u1_2.effect.dashboard.presentation.dto;

import com.u1_2.effect.dashboard.domain.DashboardWidget;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record SaveDashboardLayoutRequest(@NotEmpty List<@NotNull @Valid DashboardWidget> layout) {
}
