package com.u1_2.effect.user.presentation.dto;

import com.u1_2.effect.user.domain.Role;
import com.u1_2.effect.user.domain.User;

public record LoginResponse(
        String token,
        UserSummary user
) {
    public static LoginResponse of(String accessToken, User user) {
        return new LoginResponse(accessToken, UserSummary.from(user));
    }

    public record UserSummary(String name, Role role, String roleLabel) {
        static UserSummary from(User user) {
            return new UserSummary(user.getName(), user.getRole(), user.getRole().getLabel());
        }
    }
}
