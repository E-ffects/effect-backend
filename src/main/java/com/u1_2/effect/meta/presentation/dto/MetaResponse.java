package com.u1_2.effect.meta.presentation.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 접수 화면에서 쓰는 선택지 목록과 시연 기준일을 전달함.
 */
public record MetaResponse(
        List<CustomerOption> customers,
        List<ProductOption> products,
        List<DetectionStageOption> detectionStages,
        List<String> customerRequests,
        LocalDate referenceDate
) {
    public record CustomerOption(String customerId, String customerName) {
    }

    public record ProductOption(String specId, String partNo) {
    }

    public record DetectionStageOption(String code, String label) {
    }
}
