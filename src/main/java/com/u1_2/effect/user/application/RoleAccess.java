package com.u1_2.effect.user.application;

import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.global.exception.AuthErrorCode;
import com.u1_2.effect.user.domain.Role;

public final class RoleAccess {

    private RoleAccess() {
    }

    // 업무 서비스에서 같은 역할 검사와 권한 오류를 사용함
    public static void require(Role actual, Role allowed) {
        if (actual != allowed) {
            throw new ApiException(AuthErrorCode.ACCESS_DENIED);
        }
    }

    public static void requireMember(Role role) {
        if (role != Role.CS && role != Role.QUALITY) {
            throw new ApiException(AuthErrorCode.ACCESS_DENIED);
        }
    }
}
