package com.u1_2.effect.rma.domain.analysis;

import java.util.List;

public final class AnalysisResultStates {

    // 일부 문안 생성에 실패해도 저장된 분석 결과는 화면·판정·보고서에서 사용할 수 있음
    public static final List<String> WITH_RESULT = List.of("SUCCEEDED", "PARTIAL");

    private AnalysisResultStates() {
    }

    public static boolean hasResult(String state) {
        return state != null && WITH_RESULT.contains(state);
    }
}
