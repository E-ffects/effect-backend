package com.u1_2.effect.rma.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * DB에 저장할 발견 단계 코드와 화면에 표시할 이름을 함께 관리함.
 */
@Getter
@RequiredArgsConstructor
public enum DetectionStage {
    CUSTOMER_IQC("고객 수입검사"),
    SMT_REFLOW("SMT·리플로"),
    ICT_FUNCTION("ICT·기능검사"),
    CUSTOMER_RELIABILITY("고객 신뢰성 시험"),
    FIELD("현장 사용");

    private final String label;
}
