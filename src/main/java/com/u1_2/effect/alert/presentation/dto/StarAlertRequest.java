package com.u1_2.effect.alert.presentation.dto;

import jakarta.validation.constraints.NotNull;

public record StarAlertRequest(@NotNull Boolean starred) {
}
