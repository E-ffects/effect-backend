package com.u1_2.effect.alert.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AlertIdsRequest(@NotNull @Size(max = 100) List<@NotNull @Positive Long> ids) {
}
