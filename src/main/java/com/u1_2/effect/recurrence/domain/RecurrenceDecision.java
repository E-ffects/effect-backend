package com.u1_2.effect.recurrence.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.global.exception.CommonErrorCode;

public enum RecurrenceDecision {

    RECURRENCE("재발"),
    SPREAD_RECURRENCE("확산형 재발"),
    NEW("신규");

    private final String label;

    RecurrenceDecision(String label) {
        this.label = label;
    }

    @JsonValue
    public String label() {
        return label;
    }

    // 화면의 한글 선택값과 저장에 쓰는 고정 코드가 같은 판정을 가리킴
    @JsonCreator
    public static RecurrenceDecision fromWire(String value) {
        for (var decision : values()) {
            if (decision.label.equals(value) || decision.name().equals(value)) {
                return decision;
            }
        }
        throw new ApiException(CommonErrorCode.INVALID_INPUT);
    }
}
