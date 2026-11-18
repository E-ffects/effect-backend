package com.u1_2.effect.config.jwt;

import com.u1_2.effect.user.domain.Role;

/**
 * SecurityContext에 들어가는 로그인 사용자.
 */
public record AuthUser(Long id, Role role) {
}
