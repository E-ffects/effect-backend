package com.u1_2.effect.dashboard.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DashboardWidget(
        @NotBlank @Size(max = 40) String id,
        @NotNull @Pattern(regexp = "quarter|half|full") String width,
        @NotNull Boolean hidden
) {
}
