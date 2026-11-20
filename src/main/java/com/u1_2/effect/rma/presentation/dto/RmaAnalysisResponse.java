package com.u1_2.effect.rma.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import tools.jackson.databind.JsonNode;

/**
 * 분석 상태에 따라 진행도·결과·오류 중 해당 값과 최근 갱신 시각을 전달함.
 */
public record RmaAnalysisResponse(
        String rmaId, String analysisId, String analysisState, OffsetDateTime asOf, String mode,
        JsonNode progress, JsonNode result,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonNode trace,
        JsonNode warnings, JsonNode error, OffsetDateTime updatedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) String langsmithRunId
) {
}
