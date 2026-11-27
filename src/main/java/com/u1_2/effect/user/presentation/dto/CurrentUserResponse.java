package com.u1_2.effect.user.presentation.dto;

import com.u1_2.effect.user.domain.Role;
import com.u1_2.effect.user.domain.User;

/**
 * 인증된 사용자의 화면 표시 정보만 담음. 비밀번호나 토큰은 포함하지 않음.
 */
public record CurrentUserResponse(Long id, String username, String name, Role role, String roleLabel, boolean isAdmin) {

    public static CurrentUserResponse from(User user) {
        return new CurrentUserResponse(user.getId(), user.getUsername(), user.getName(),
                user.getRole(), user.getRole().getLabel(), user.isAdmin());
    }
}
