package com.u1_2.effect.rma.presentation.analysis;

import com.u1_2.effect.config.jwt.AuthUser;
import com.u1_2.effect.global.annotation.CurrentUser;
import com.u1_2.effect.global.dto.ResponseDto;
import com.u1_2.effect.rma.application.analysis.RmaAnalysisService;
import com.u1_2.effect.rma.application.analysis.RmaSimilarService;
import com.u1_2.effect.rma.presentation.analysis.dto.RmaAnalysisResponse;
import com.u1_2.effect.rma.presentation.analysis.dto.RmaSimilarResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RmaAnalysisQueryController {

    private final RmaAnalysisService rmaAnalysisService;
    private final RmaSimilarService rmaSimilarService;

    @Operation(summary = "RMA 최근 원인분석 상태 및 결과 조회")
    @GetMapping("/rmas/{rmaId}/analysis")
    public ResponseEntity<ResponseDto<RmaAnalysisResponse>> getAnalysis(
            @PathVariable String rmaId, @CurrentUser AuthUser authUser
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(rmaAnalysisService.getAnalysis(rmaId, authUser.role())));
    }

    @Operation(summary = "RMA의 과거 유사 사례 조회")
    @GetMapping("/rmas/{rmaId}/similar")
    public ResponseEntity<ResponseDto<RmaSimilarResponse>> getSimilar(
            @PathVariable String rmaId, @RequestParam(defaultValue = "5") @Min(1) @Max(20) int limit,
            @CurrentUser AuthUser authUser
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(rmaSimilarService.getSimilar(rmaId, limit, authUser.role())));
    }
}
