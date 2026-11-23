package com.u1_2.effect.report.domain;

import java.time.OffsetDateTime;
import java.util.List;

public record ReportBody(
        String rmaId, String analysisId, String title, String mainCause, String qualityRecommendation,
        String csRecommendation, List<String> similar, List<String> evidence, String disclaimer,
        String editedBy, OffsetDateTime editedAt
) {
    // 본문 수정은 사람이 편집하는 세 필드만 바꾸고 AI 분석 출처와 연결 근거는 유지함
    public ReportBody edit(String cause, String quality, String cs, String editor, OffsetDateTime time) {
        return new ReportBody(rmaId, analysisId, title, cause, quality, cs, similar, evidence, disclaimer, editor, time);
    }
}
