package com.u1_2.effect.report.application;

import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.report.domain.ReportBody;
import com.u1_2.effect.report.domain.ReportErrorCode;
import com.u1_2.effect.rma.domain.RmaClaimRepository;
import com.u1_2.effect.rma.domain.RmaErrorCode;
import com.u1_2.effect.rma.domain.analysis.RmaAnalysisRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
class ReportDraftFactory {

    private final RmaAnalysisRepository analyses;
    private final RmaClaimRepository claims;
    private final JsonMapper jsonMapper;

    Draft build(String rmaId) {
        var analysis = analyses.findLatest(rmaId).orElseThrow(() -> new ApiException(RmaErrorCode.RMA_NOT_FOUND));
        if (analysis.result() == null || !("SUCCEEDED".equals(analysis.analysisState()) || "PARTIAL".equals(analysis.analysisState()))) {
            throw new ApiException(ReportErrorCode.REPORT_ANALYSIS_NOT_READY);
        }

        var result = jsonMapper.readTree(analysis.result());
        if (!result.isObject()) {
            throw new ApiException(ReportErrorCode.REPORT_ANALYSIS_NOT_READY);
        }
        var topCause = result.path("cause_candidates").path(0);
        var drafts = result.path("drafts");
        var similarCases = similarCases(result.path("similar_cases"));

        // 원인 후보와 유사 사례는 분석 결과의 구조화된 값에서만 가져오고 실제 RMA만 근거 링크로 남김
        var rmaIds = new LinkedHashSet<String>();
        rmaIds.add(rmaId);
        similarCases.forEach(row -> rmaIds.add(row.rmaId()));
        for (JsonNode evidence : topCause.path("evidence")) {
            if (rmaIds.size() >= 20) {
                break;
            }
            if ("rma_claim".equals(evidence.path("ref_table").asString(""))) {
                String id = text(evidence.path("ref_id"));
                if (id != null) {
                    rmaIds.add(id);
                }
            }
        }
        var known = claims.existingIds(rmaIds);
        var evidence = rmaIds.stream().filter(known::contains).toList();
        var similar = similarCases.stream().filter(row -> known.contains(row.rmaId()))
                .map(SimilarCase::label).distinct().toList();

        return new Draft(new ReportBody(rmaId, analysis.analysisId(), rmaId + " RMA 분석 보고서",
                causeText(topCause), improvement(drafts.path("improvement")),
                fallback(drafts.path("customer_reply"), "분석 결과와 처리 상태를 확인한 뒤 고객에게 회신해 주세요."),
                similar, evidence, "저장된 AI 분석을 바탕으로 정리한 초안입니다. 원인과 조치를 검토한 뒤 사용하세요.", null, null),
                text(result.at("/classification/minor_name")));
    }

    private String causeText(JsonNode cause) {
        String name = text(cause.path("cause_name"));
        if (name == null) {
            return "원인 후보 검토 대기";
        }
        String explanation = text(cause.path("explanation"));
        return "원인 후보: " + name + (explanation == null ? "" : "\n" + explanation);
    }

    private String improvement(JsonNode improvement) {
        var actions = new LinkedHashSet<String>();
        for (String group : List.of("process", "inspection")) {
            for (JsonNode action : improvement.path(group)) {
                String value = text(action.path("action"));
                if (value != null) {
                    actions.add(value);
                }
            }
        }
        return actions.isEmpty() ? "개선 조치는 품질 담당자가 분석 근거를 검토한 뒤 기록해 주세요." : String.join("\n", actions);
    }

    private List<SimilarCase> similarCases(JsonNode cases) {
        var similar = new ArrayList<SimilarCase>();
        for (JsonNode item : cases) {
            if (similar.size() == 5) {
                break;
            }
            String id = text(item.path("rma_id"));
            if (id != null) {
                String symptom = text(item.path("minor_name"));
                similar.add(new SimilarCase(id, symptom == null ? id : id + " · " + symptom));
            }
        }
        return similar;
    }

    private String fallback(JsonNode value, String defaultText) {
        String text = text(value);
        return text == null ? defaultText : text;
    }

    private String text(JsonNode node) {
        String value = node.asString(null);
        return StringUtils.hasText(value) ? value.strip() : null;
    }

    record Draft(ReportBody body, String defectType) {
    }

    private record SimilarCase(String rmaId, String label) {
    }
}
