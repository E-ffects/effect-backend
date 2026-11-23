package com.u1_2.effect.rma.domain.intake;

import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 접수 폼과 서버 검증이 같은 고객 요청 선택지를 사용하도록 관리함.
 */
@Getter
@RequiredArgsConstructor
public enum CustomerRequest {
    REPLACEMENT_SHIPMENT("대체품 출하"),
    CREDIT_NOTE("대금 공제"),
    SORTING_SUPPORT_AND_CREDIT("선별 지원·대금 공제");

    private final String label;

    public static List<String> labels() {
        return Arrays.stream(values()).map(CustomerRequest::getLabel).toList();
    }
}
