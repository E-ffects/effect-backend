package com.u1_2.effect.user.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Role {
    QUALITY("품질관리자"),
    CS("고객대응관리자");

    private final String label;

    public String getAuthority() {
        return "ROLE_" + name();
    }
}
