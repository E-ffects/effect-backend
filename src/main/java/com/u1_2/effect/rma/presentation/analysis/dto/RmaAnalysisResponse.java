package com.u1_2.effect.rma.presentation.analysis.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import tools.jackson.databind.JsonNode;

public record RmaAnalysisResponse(
        String rmaId, String analysisId, String analysisState, OffsetDateTime asOf, String mode,
        JsonNode progress, JsonNode result,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonNode trace,
        JsonNode warnings, JsonNode error, OffsetDateTime updatedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) String langsmithRunId
) {
}
