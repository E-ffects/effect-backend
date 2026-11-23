package com.u1_2.effect.rma.presentation.analysis.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 재분석 시 모드와 후보 수를 선택할 수 있음. 생략 시 서비스에서 기본값을 적용함.
 */
public record CreateRmaAnalysisRequest(Mode mode, @Min(1) @Max(10) Integer topK) {

    public enum Mode { A, B }
}
