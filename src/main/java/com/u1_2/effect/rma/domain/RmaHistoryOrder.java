package com.u1_2.effect.rma.domain;

/**
 * 같은 접수의 이력을 여러 화면에서 읽을 때 시각이 같아도 같은 기록을 선택하도록 순서를 공유함.
 */
public final class RmaHistoryOrder {

    public static final String ANALYSIS =
            "analysis.created_at DESC, analysis.poll_started_at DESC, analysis.analysis_id DESC";
    public static final String TRIAGE = "triage.decided_at DESC, triage.triage_id DESC";
    public static final String SYMPTOM = "symptom.created_at DESC, symptom.assessment_id DESC";

    private RmaHistoryOrder() {
    }
}
