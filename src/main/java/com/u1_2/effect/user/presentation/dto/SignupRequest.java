package com.u1_2.effect.user.presentation.dto;

import com.u1_2.effect.user.domain.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank @Size(min = 2, max = 20) String name,
        @NotBlank @Size(min = 3, max = 100) String username,
        @NotBlank @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{8,64}$") String password,
        @NotNull Role role
) {
}
